package me.liwncy.spiders.task;

import cn.hutool.core.util.StrUtil;
import me.liwncy.common.crawler.core.AbstractSpider;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.core.SpiderFramework;
import me.liwncy.common.crawler.core.SpiderParseResult;
import me.liwncy.spiders.support.SpiderPaths;
import me.liwncy.spiders.support.browser.ChromeDriverSupport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中国裁判文书网列表抓取任务。
 * <p>
 * 当前版本采用“单浏览器串行流程”：
 * 1. 同一浏览器实例直接打开目标页；
 * 2. 若跳到登录页，则等待用户在浏览器中手工完成登录；
 * 3. 登录完成后在当前实例中填写高级检索裁判日期范围并执行查询；
 * 4. 逐页翻页，汇总所有检索结果；
 * 5. 提取标题、法院、案号、裁判日期等关键字段。
 */
@Component
public class WenshuCourtSpider extends AbstractSpider {

    public static final String NAME = "wenshu-court";

    private static final String PROPERTY_PREFIX = "spider.wenshu.court.";
    private static final String DEFAULT_TARGET_URL = "https://wenshu.court.gov.cn/website/wenshu/181217BMTKHNT2W0/index.html";
    private static final String DEFAULT_EXPECTED_KEYWORD = "";
    private static final String DEFAULT_LOGIN_PAGE_KEYWORD = "登录/注册";
    private static final long DEFAULT_RENDER_WAIT_MILLIS = 12000L;
    private static final long DEFAULT_MANUAL_LOGIN_TIMEOUT_MILLIS = 600000L;
    private static final long DEFAULT_JUDGMENT_DATE_OFFSET_DAYS = 1L;
    private static final String DEFAULT_JUDGMENT_END_DATE = "2099-01-01";
    private static final long DEFAULT_DETAIL_RENDER_WAIT_MILLIS = 20000L;
    private static final boolean DEFAULT_EXTRACT_DETAIL_ENABLED = true;
    private static final String DEFAULT_SOURCE_NAME = "中国裁判文书网";
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[年\\-/\\.](\\d{1,2})[月\\-/\\.](\\d{1,2})日?");
    private static final Pattern ACCEPT_DATE_PATTERN = Pattern.compile("(?:于|在)(20\\d{2}年\\d{1,2}月\\d{1,2}日).{0,20}?(?:立案|受理)");
    private static final Pattern CASE_YEAR_PATTERN = Pattern.compile("[（(](20\\d{2})[)）]");
    private static final List<String> DOC_TYPE_SUFFIXES = List.of("判决书", "裁定书", "调解书", "决定书", "通知书", "支付令", "执行通知书", "执行裁定书");
    private static final List<String> PROVINCE_NAMES = List.of(
        "新疆维吾尔自治区", "内蒙古自治区", "广西壮族自治区", "宁夏回族自治区", "西藏自治区",
        "北京市", "天津市", "上海市", "重庆市",
        "香港特别行政区", "澳门特别行政区",
        "黑龙江省", "吉林省", "辽宁省", "河北省", "山西省", "陕西省", "甘肃省", "青海省", "山东省", "江苏省",
        "浙江省", "安徽省", "福建省", "江西省", "河南省", "湖北省", "湖南省", "广东省", "海南省", "四川省",
        "贵州省", "云南省", "台湾省"
    );

    @Override
    public SpiderConfig getConfig() {
        Settings settings = Settings.load(this);
        return SpiderConfig.builder()
            .name(NAME)
            .description("中国裁判文书网列表抓取任务：同一浏览器中完成登录，按高级检索裁判日期范围导出全部分页结果")
            .framework(SpiderFramework.WEBMAGIC)
            .startUrls(List.of(settings.targetUrl()))
            .threadCount(1)
            .pipelines(List.of("console", "file"))
            .build();
    }

    @Override
    public SpiderParseResult parse(String htmlText, String url) {
        Settings settings = Settings.load(this);
        return crawlInSingleBrowser(settings);
    }

    private SpiderParseResult crawlInSingleBrowser(Settings settings) {
        WebDriver webDriver = ChromeDriverSupport.createChromeDriver(settings.browserUserDataDir());
        try {
            webDriver.get(settings.targetUrl());
            waitForReadyPageAfterLogin(webDriver, settings);
            SpiderParseResult result = collectSearchResults(webDriver, settings);
            enrichDetailsIfNecessary(webDriver, settings, result);
            return result;
        } finally {
            webDriver.quit();
        }
    }

    private SpiderParseResult collectSearchResults(WebDriver webDriver, Settings settings) {
        SearchCriteria baseCriteria = SearchCriteria.singleDay(settings.targetJudgmentDateStart(), settings.targetJudgmentDateEnd());
        applyAdvancedDateSearch(webDriver, settings, baseCriteria);
        waitForListPage(webDriver, settings);
        int totalResultCount = totalResultCount(webDriver);
        if (totalResultCount <= 600) {
            return collectAllPages(webDriver, settings, true);
        }

        List<DocumentTypeOption> documentTypes = loadDocumentTypeOptions(webDriver);
        if (documentTypes.isEmpty()) {
            throw new IllegalStateException("文书网检索结果超过 600 条，但未识别到可用于拆分的文书类型选项。");
        }

        System.out.println("文书网结果超过 600 条，开始按文书类型拆分抓取。类型数=" + documentTypes.size());
        SpiderParseResult aggregated = SpiderParseResult.empty();
        Set<String> seenDocIds = new LinkedHashSet<>();
        for (DocumentTypeOption documentType : documentTypes) {
            SearchCriteria criteria = baseCriteria.withDocumentType(documentType.code(), documentType.name());
            applyAdvancedDateSearch(webDriver, settings, criteria);
            waitForListPage(webDriver, settings);
            int currentTypeCount = totalResultCount(webDriver);
            if (currentTypeCount == 0 || containsNoData(webDriver)) {
                continue;
            }
            if (currentTypeCount > 600) {
                System.out.println("文书类型 " + documentType.name() + " 仍超过 600 条，继续按案件类型拆分。");
                SpiderParseResult splitByCaseType = collectByCaseType(webDriver, settings, criteria);
                mergeParseResult(aggregated, splitByCaseType, seenDocIds);
                continue;
            }
            SpiderParseResult partial = collectAllPages(webDriver, settings, false);
            mergeParseResult(aggregated, partial, seenDocIds);
        }

        if (aggregated.getItems().isEmpty()) {
            throw new IllegalStateException("按文书类型拆分后仍未抓取到任何结果，请检查文书类型条件是否被正确应用。");
        }
        return aggregated;
    }

    private SpiderParseResult collectByCaseType(WebDriver webDriver, Settings settings, SearchCriteria baseCriteria) {
        List<CaseTypeOption> caseTypes = loadCaseTypeOptions(webDriver);
        if (caseTypes.isEmpty()) {
            throw new IllegalStateException(
                "文书网在文书类型=" + baseCriteria.documentTypeName() + " 下结果仍超过 600 条，但未识别到可用于继续拆分的案件类型选项。"
            );
        }

        SpiderParseResult aggregated = SpiderParseResult.empty();
        Set<String> seenDocIds = new LinkedHashSet<>();
        for (CaseTypeOption caseType : caseTypes) {
            SearchCriteria criteria = baseCriteria.withCaseType(caseType.code(), caseType.name());
            applyAdvancedDateSearch(webDriver, settings, criteria);
            waitForListPage(webDriver, settings);
            int currentCaseTypeCount = totalResultCount(webDriver);
            if (currentCaseTypeCount == 0 || containsNoData(webDriver)) {
                continue;
            }
            ensureVisibleResultWindowComplete(
                currentCaseTypeCount,
                "文书类型=" + baseCriteria.documentTypeName() + ", 案件类型=" + caseType.name()
            );
            SpiderParseResult partial = collectAllPages(webDriver, settings, false);
            mergeParseResult(aggregated, partial, seenDocIds);
        }

        if (aggregated.getItems().isEmpty()) {
            throw new IllegalStateException(
                "按案件类型拆分后仍未抓取到结果，请检查案件类型条件是否被正确应用。文书类型=" + baseCriteria.documentTypeName()
            );
        }
        return aggregated;
    }

    private void waitForReadyPageAfterLogin(WebDriver webDriver, Settings settings) {
        RenderedPage initialPage = capturePage(webDriver);
        if (isLoginPage(initialPage, settings)) {
            System.out.println("检测到文书网登录页，请在当前浏览器中完成账号密码和图片验证码输入。");
            System.out.println("登录成功后程序会自动继续，无需回控制台按回车。");
        }

        try {
            new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.manualLoginTimeoutMillis(), 1000L)))
                .until(driver -> {
                    RenderedPage page = capturePage(driver);
                    if (isLoginPage(page, settings)) {
                        return false;
                    }
                    return hasAdvancedDateInputs(driver)
                        || hasListItems(driver)
                        || StrUtil.contains(page.html(), "暂无数据");
                });
        } catch (TimeoutException e) {
            RenderedPage renderedPage = capturePage(webDriver);
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "等待登录完成或目标页加载超时。"
                    + " currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath,
                e
            );
        }
    }

    private void applyAdvancedDateSearch(WebDriver webDriver, Settings settings, SearchCriteria criteria) {
        waitForAdvancedSearchInputs(webDriver, settings);
        if (StrUtil.isBlank(criteria.judgmentDateStart()) && StrUtil.isBlank(criteria.judgmentDateEnd())) {
            return;
        }
        resetSearchConditions(webDriver, settings);
        openAdvancedSearchIfNecessary(webDriver);
        if (!setInputValue(webDriver, "#cprqStart", criteria.judgmentDateStart())
            || !setInputValue(webDriver, "#cprqEnd", criteria.judgmentDateEnd())) {
            RenderedPage renderedPage = capturePage(webDriver);
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "未找到高级检索裁判日期输入框，无法注入时间范围。"
                    + " currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath
            );
        }
        setDocumentTypeCondition(webDriver, criteria);
        setCaseTypeCondition(webDriver, criteria);
        String expectedDateRange = buildExpectedDateRange(criteria.judgmentDateStart(), criteria.judgmentDateEnd());
        List<WebElement> searchButtons = webDriver.findElements(By.cssSelector("#searchBtn"));
        if (searchButtons.isEmpty()) {
            RenderedPage renderedPage = capturePage(webDriver);
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "未找到高级检索按钮 #searchBtn。"
                    + " currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath
            );
        }
        searchButtons.get(0).click();
        waitForAppliedSearchConditions(webDriver, settings, criteria, expectedDateRange);
        verifyAppliedSearchConditions(webDriver, criteria, expectedDateRange);
    }

    private void waitForAdvancedSearchInputs(WebDriver webDriver, Settings settings) {
        try {
            new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.renderWaitMillis(), 3000L)))
                .until(this::hasAdvancedDateInputs);
        } catch (TimeoutException e) {
            RenderedPage renderedPage = capturePage(webDriver);
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "文书网高级检索裁判日期输入框未加载完成，无法注入时间范围。"
                    + " currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath,
                e
            );
        }
    }

    private void waitForListPage(WebDriver webDriver, Settings settings) {
        try {
            new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.renderWaitMillis(), 3000L)))
                .until(driver -> {
                    RenderedPage page = capturePage(driver);
                    return hasListItems(driver)
                        || StrUtil.contains(page.html(), "暂无数据")
                        || isLoginPage(page, settings);
                });
        } catch (TimeoutException ignored) {
            // 交由后续解析抛出更明确的异常。
        }
    }

    private SpiderParseResult collectAllPages(WebDriver webDriver, Settings settings, boolean validateKeyword) {
        SpiderParseResult result = SpiderParseResult.empty();
        Set<String> seenDocIds = new LinkedHashSet<>();
        boolean anyKeywordMatched = StrUtil.isBlank(settings.expectedKeyword());
        int pageNumber = 1;

        while (true) {
            RenderedPage renderedPage = capturePage(webDriver);
            ParsedPage parsedPage = parseRenderedPage(renderedPage, settings, pageNumber);
            anyKeywordMatched = anyKeywordMatched || parsedPage.keywordMatched();

            for (CrawlResult item : parsedPage.items()) {
                String docId = StrUtil.nullToEmpty(String.valueOf(item.get("docId"))).trim();
                if (StrUtil.isBlank(docId) || seenDocIds.add(docId)) {
                    result.addItem(item);
                }
            }

            if (!goToNextPage(webDriver, settings)) {
                break;
            }
            pageNumber++;
        }

        if (validateKeyword && !anyKeywordMatched) {
            throw new IllegalStateException("文书列表已渲染，但未匹配到 expectedKeyword=" + settings.expectedKeyword() + "，请检查目标页或检索条件。");
        }
        if (result.getItems().isEmpty()) {
            throw new IllegalStateException("文书列表分页已遍历完成，但没有可导出的结果，请检查筛选条件。");
        }
        return result;
    }

    private void mergeParseResult(SpiderParseResult target, SpiderParseResult source, Set<String> seenDocIds) {
        for (CrawlResult item : source.getItems()) {
            String docId = StrUtil.nullToEmpty(String.valueOf(item.get("docId"))).trim();
            if (StrUtil.isBlank(docId) || seenDocIds.add(docId)) {
                target.addItem(item);
            }
        }
    }

    private void enrichDetailsIfNecessary(WebDriver webDriver, Settings settings, SpiderParseResult result) {
        if (!settings.extractDetailEnabled() || result.getItems().isEmpty()) {
            return;
        }
        System.out.println("开始提取文书详情，共 " + result.getItems().size() + " 条。");
        int successCount = 0;
        for (int i = 0; i < result.getItems().size(); i++) {
            CrawlResult item = result.getItems().get(i);
            Object detailUrlValue = item.get("detailUrl");
            String detailUrl = detailUrlValue == null ? StrUtil.EMPTY : StrUtil.trim(String.valueOf(detailUrlValue));
            if (StrUtil.isBlank(detailUrl)) {
                item.field("detailExtracted", false)
                    .field("detailError", "detailUrl 为空，跳过详情提取");
                continue;
            }
            try {
                DetailPage detailPage = loadDetailPage(webDriver, settings, detailUrl);
                applyDetailFields(item, detailPage);
                successCount++;
                if ((i + 1) % 10 == 0 || i + 1 == result.getItems().size()) {
                    System.out.println("文书详情提取进度：" + (i + 1) + "/" + result.getItems().size());
                }
            } catch (RuntimeException e) {
                item.field("detailExtracted", false)
                    .field("detailError", summarizeExceptionMessage(e));
                System.err.println("文书详情提取失败：index=" + (i + 1) + ", detailUrl=" + detailUrl + ", error=" + summarizeExceptionMessage(e));
            }
        }
        if (successCount == 0) {
            throw new IllegalStateException("文书详情页提取全部失败，请检查登录态或详情页结构是否发生变化。");
        }
    }

    private DetailPage loadDetailPage(WebDriver webDriver, Settings settings, String detailUrl) {
        webDriver.get(detailUrl);
        RenderedPage renderedPage = waitForDetailPageReady(webDriver, settings, detailUrl);
        if (isLoginPage(renderedPage, settings)) {
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "进入文书详情页后跳回登录页。"
                    + " detailUrl=" + detailUrl
                    + ", currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath
            );
        }
        return parseDetailPage(renderedPage, detailUrl);
    }

    private RenderedPage waitForDetailPageReady(WebDriver webDriver, Settings settings, String detailUrl) {
        try {
            new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.detailRenderWaitMillis(), 3000L)))
                .until(driver -> {
                    RenderedPage page = capturePage(driver);
                    if (isLoginPage(page, settings)) {
                        return true;
                    }
                    return hasRenderedDetailContent(page, detailUrl);
                });
        } catch (TimeoutException e) {
            RenderedPage renderedPage = capturePage(webDriver);
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "等待文书详情页渲染超时。"
                    + " detailUrl=" + detailUrl
                    + ", currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath,
                e
            );
        }
        return capturePage(webDriver);
    }

    private boolean hasRenderedDetailContent(RenderedPage renderedPage, String baseUri) {
        Document document = Jsoup.parse(renderedPage.html(), baseUri);
        String documentTitle = normalizeMultilineText(text(document.selectFirst(".PDF_title")));
        String documentContent = normalizeMultilineText(wholeText(document.selectFirst(".PDF_pox")));
        return StrUtil.isNotBlank(documentTitle)
            || StrUtil.isNotBlank(documentContent)
            || !document.select(".detail_guanlian .guanLian a, .del_right .gaiyao_center h4").isEmpty();
    }

    private DetailPage parseDetailPage(RenderedPage renderedPage, String baseUri) {
        Document document = Jsoup.parse(renderedPage.html(), baseUri);
        String documentTitle = normalizeMultilineText(text(document.selectFirst(".PDF_title")));
        String documentContent = normalizeMultilineText(wholeText(document.selectFirst(".PDF_pox")));
        String caseLevel = normalizeMultilineText(text(document.selectFirst(".PDF_cut table.dftable tr:nth-of-type(2) td:nth-of-type(2)")));
        List<String> summaryLines = parseDetailSummaryLines(document);
        List<Map<String, Object>> legalBasis = parseDetailLegalBasis(document);
        List<Map<String, Object>> relatedDocuments = parseDetailRelatedDocuments(document);
        if (StrUtil.isBlank(documentTitle)
            && StrUtil.isBlank(documentContent)
            && summaryLines.isEmpty()
            && legalBasis.isEmpty()
            && relatedDocuments.isEmpty()) {
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "文书详情页已打开，但未解析到正文或属性内容。"
                    + " currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath
            );
        }
        return new DetailPage(
            renderedPage.currentUrl(),
            renderedPage.title(),
            documentTitle,
            documentContent,
            caseLevel,
            summaryLines,
            legalBasis,
            relatedDocuments
        );
    }

    private void applyDetailFields(CrawlResult item, DetailPage detailPage) {
        Map<String, Object> caseRecord = buildCaseRecord(item, detailPage);
        item.field("detailExtracted", true)
            .field("detailCurrentUrl", detailPage.currentUrl())
            .field("detailPageTitle", detailPage.pageTitle())
            .field("detailDocumentTitle", detailPage.documentTitle())
            .field("detailContent", detailPage.documentContent())
            .field("detailContentLength", detailPage.documentContent().length())
            .field("detailCaseLevel", detailPage.caseLevel())
            .field("detailSummaryLines", detailPage.summaryLines())
            .field("detailLegalBasis", detailPage.legalBasis())
            .field("detailRelatedDocuments", detailPage.relatedDocuments())
            .field("case_record", caseRecord)
            .field("detailError", StrUtil.EMPTY);
        for (Map.Entry<String, Object> entry : caseRecord.entrySet()) {
            item.field(entry.getKey(), entry.getValue());
        }
    }

    private Map<String, Object> buildCaseRecord(CrawlResult item, DetailPage detailPage) {
        String docId = safeString(item.get("docId"));
        String recordTitle = firstNonBlank(detailPage.documentTitle(), safeString(item.get("title")));
        String court = firstNonBlank(summaryLine(detailPage, 0), safeString(item.get("court")));
        String caseType = firstNonBlank(summaryLine(detailPage, 1), inferCaseType(recordTitle, safeString(item.get("trialProcedure"))));
        String cause = firstNonBlank(summaryLine(detailPage, 2), inferCause(recordTitle));
        String trialProcedure = firstNonBlank(summaryLine(detailPage, 3), safeString(item.get("trialProcedure")));
        String judgeDate = firstNonBlank(normalizeDateText(summaryLine(detailPage, 4)), normalizeDateText(safeString(item.get("judgmentDate"))));
        String publishDate = firstNonBlank(normalizeDateText(summaryLine(detailPage, 5)), null);
        String acceptDate = extractAcceptDate(detailPage.documentContent());
        String docType = extractDocType(recordTitle);
        String courtLevel = inferCourtLevel(court);
        CourtLocation courtLocation = parseCourtLocation(court);
        Integer judgeYear = extractJudgeYear(judgeDate, safeString(item.get("caseNumber")));
        String result = extractResult(detailPage.documentContent());
        String lawBasis = buildLawBasisText(detailPage.legalBasis());
        String content = buildFullContent(detailPage);
        List<Map<String, Object>> parties = extractParties(detailPage.documentContent());
        String sourceUrl = firstNonBlank(detailPage.currentUrl(), safeString(item.get("detailUrl")), safeString(item.get("sourceUrl")));

        Map<String, Object> caseRecord = new LinkedHashMap<>();
        caseRecord.put("id", generateRecordId(docId, sourceUrl));
        caseRecord.put("case_id", firstNonBlank(safeString(item.get("caseNumber")), extractCaseIdFromContent(detailPage.documentContent())));
        caseRecord.put("doc_type", blankToNull(docType));
        caseRecord.put("court", blankToNull(court));
        caseRecord.put("case_type", blankToNull(caseType));
        caseRecord.put("title", blankToNull(recordTitle));
        caseRecord.put("case_type_code", null);
        caseRecord.put("trial_procedure", blankToNull(trialProcedure));
        caseRecord.put("judge_date", blankToNull(judgeDate));
        caseRecord.put("publish_date", blankToNull(publishDate));
        caseRecord.put("accept_date", blankToNull(acceptDate));
        caseRecord.put("case_level", blankToNull(detailPage.caseLevel()));
        caseRecord.put("court_level", blankToNull(courtLevel));
        caseRecord.put("cause", blankToNull(cause));
        caseRecord.put("province", blankToNull(courtLocation.province()));
        caseRecord.put("city", blankToNull(courtLocation.city()));
        caseRecord.put("region", blankToNull(courtLocation.region()));
        caseRecord.put("judge_year", judgeYear);
        caseRecord.put("result", blankToNull(result));
        caseRecord.put("law_basis", blankToNull(lawBasis));
        caseRecord.put("content", blankToNull(content));
        caseRecord.put("source_url", blankToNull(sourceUrl));
        caseRecord.put("source", DEFAULT_SOURCE_NAME);
        caseRecord.put("parties", parties.isEmpty() ? null : parties);
        return caseRecord;
    }

    private void switchToMaxPageSize(WebDriver webDriver, Settings settings) {
        List<WebElement> options = webDriver.findElements(By.cssSelector("select.pageSizeSelect option"));
        if (options.isEmpty()) {
            return;
        }

        int maxPageSize = Integer.MIN_VALUE;
        String selectedValue = StrUtil.EMPTY;
        for (WebElement option : options) {
            String text = StrUtil.trim(option.getText());
            if (StrUtil.isBlank(text)) {
                continue;
            }
            try {
                int current = Integer.parseInt(text);
                if (current > maxPageSize) {
                    maxPageSize = current;
                    selectedValue = text;
                }
            } catch (NumberFormatException ignored) {
                // 忽略非数字页容量选项。
            }
        }

        if (maxPageSize == Integer.MIN_VALUE) {
            return;
        }

        List<WebElement> pageSizeSelects = webDriver.findElements(By.cssSelector("select.pageSizeSelect"));
        if (pageSizeSelects.isEmpty()) {
            return;
        }

        WebElement select = pageSizeSelects.get(0);
        String currentFirstDocId = firstVisibleDocId(webDriver);
        String currentValue = StrUtil.trim(select.getAttribute("value"));
        int currentVisibleCount = countVisibleListItems(webDriver);
        int totalResultCount = totalResultCount(webDriver);
        if (StrUtil.equals(currentValue, selectedValue)) {
            return;
        }
        String targetPageSize = selectedValue;

        ((JavascriptExecutor) webDriver).executeScript(
            "arguments[0].value = arguments[1];"
                + "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
            select,
            targetPageSize
        );

        try {
            new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.renderWaitMillis(), 3000L)))
                .until(driver -> {
                    String newFirstDocId = firstVisibleDocId(driver);
                    int newVisibleCount = countVisibleListItems(driver);
                    boolean noExtraRowsToShow = totalResultCount > 0 && totalResultCount <= currentVisibleCount;
                    return (!StrUtil.isBlank(newFirstDocId) && !StrUtil.equals(newFirstDocId, currentFirstDocId))
                        || newVisibleCount != currentVisibleCount
                        || (noExtraRowsToShow && StrUtil.equals(targetPageSize, selectedPageSize(driver)));
                });
        } catch (TimeoutException ignored) {
            // 页面大小切换失败时继续使用当前页容量，避免中断整次导出。
        }
    }

    private ParsedPage parseRenderedPage(RenderedPage renderedPage, Settings settings, int pageNumber) {
        String renderedHtml = renderedPage.html();
        Document document = Jsoup.parse(renderedHtml, settings.targetUrl());
        Elements items = document.select(".LM_list");
        if (items.isEmpty()) {
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            boolean loginPageDetected = isLoginPage(renderedPage, settings);
            throw new IllegalStateException(
                "未在渲染后的文书列表页中发现 .LM_list 节点。"
                    + " currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", loginPageDetected=" + loginPageDetected
                    + ", snapshot=" + snapshotPath
            );
        }

        List<CrawlResult> pageItems = new ArrayList<>();
        boolean keywordMatched = false;
        int index = 0;
        for (Element item : items) {
            Element titleAnchor = item.selectFirst(".list_title h4 a.caseName");
            String title = text(titleAnchor);
            String detailUrl = titleAnchor == null ? "" : titleAnchor.absUrl("href");
            String caseNumber = text(item.selectFirst(".list_subtitle .ah"));
            String court = text(item.selectFirst(".list_subtitle .slfyName"));
            String judgmentDate = text(item.selectFirst(".list_subtitle .cprq"));
            String trialProcedure = text(item.selectFirst(".List_label .labelTwo"));
            String reasonTitle = text(item.selectFirst(".list_reason h4"));
            String reasonSummary = text(item.selectFirst(".list_reason p"));
            List<String> labels = item.select(".List_label span").eachText();
            List<Map<String, Object>> relatedDocuments = parseRelatedDocuments(item);
            String docId = extractDocId(detailUrl);
            index++;

            if (matchesExpectedKeyword(settings.expectedKeyword(), title, caseNumber, reasonSummary)) {
                keywordMatched = true;
            }

            pageItems.add(CrawlResult.builder().build()
                .field("sourceUrl", settings.targetUrl())
                .field("targetJudgmentDateStart", settings.targetJudgmentDateStart())
                .field("targetJudgmentDateEnd", settings.targetJudgmentDateEnd())
                .field("pageTitle", renderedPage.title())
                .field("pageNumber", pageNumber)
                .field("rowIndex", index)
                .field("docId", docId)
                .field("detailUrl", detailUrl)
                .field("title", title)
                .field("court", court)
                .field("caseNumber", caseNumber)
                .field("judgmentDate", judgmentDate)
                .field("trialProcedure", trialProcedure)
                .field("reasonTitle", reasonTitle)
                .field("reasonSummary", reasonSummary)
                .field("labels", labels)
                .field("relatedDocuments", relatedDocuments)
            );
        }

        return new ParsedPage(pageItems, keywordMatched);
    }

    private boolean goToNextPage(WebDriver webDriver, Settings settings) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                List<WebElement> nextButtons = webDriver.findElements(By.cssSelector(".left_7_3 a.pageButton:last-of-type"));
                if (nextButtons.isEmpty()) {
                    return false;
                }
                WebElement nextButton = nextButtons.get(0);
                String className = StrUtil.nullToEmpty(nextButton.getAttribute("class"));
                if (className.contains("disabled")) {
                    return false;
                }

                String currentPageNumber = currentPageNumber(webDriver);
                String currentFirstDocId = firstVisibleDocId(webDriver);
                nextButton.click();
                new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.renderWaitMillis(), 3000L)))
                    .until(driver -> hasPageChanged(driver, currentPageNumber, currentFirstDocId));
                return true;
            } catch (StaleElementReferenceException e) {
                if (attempt == 1) {
                    throw e;
                }
            } catch (TimeoutException e) {
                RenderedPage renderedPage = capturePage(webDriver);
                Path snapshotPath = writeDebugSnapshot(renderedPage);
                throw new IllegalStateException(
                    "分页翻页超时，无法进入下一页。"
                        + " currentUrl=" + renderedPage.currentUrl()
                        + ", title=" + renderedPage.title()
                        + ", snapshot=" + snapshotPath,
                    e
                );
            }
        }
        return false;
    }

    private String currentPageNumber(WebDriver webDriver) {
        return textOfFirst(webDriver, ".left_7_3 a.active");
    }

    private void resetSearchConditions(WebDriver webDriver, Settings settings) {
        clearStoredSearchState(webDriver);
        String previousSignature = activeFilterSignature(webDriver);
        if (StrUtil.isBlank(previousSignature)) {
            return;
        }
        List<WebElement> clearButtons = webDriver.findElements(By.cssSelector("#clear-Btn"));
        if (!clearButtons.isEmpty()) {
            clearButtons.get(0).click();
        } else {
            ((JavascriptExecutor) webDriver).executeScript(
                "document.querySelectorAll('.LT_Filter_right.clearfix p').forEach(function(node){node.remove();});"
                    + "if(window.jQuery){window.jQuery('.list-box li').removeClass('on');}"
                    + "if(window.$page && typeof window.$page.loadData === 'function'){window.$page.loadData();}"
            );
        }
        try {
            new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.renderWaitMillis(), 3000L)))
                .until(driver -> StrUtil.isBlank(activeFilterSignature(driver)));
        } catch (TimeoutException e) {
            RenderedPage renderedPage = capturePage(webDriver);
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "清理文书网历史检索条件失败。"
                    + " currentFilters=" + previousSignature
                    + ", actualFilters=" + activeFilterSignature(webDriver)
                    + ", currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath,
                e
            );
        }
    }

    private void clearStoredSearchState(WebDriver webDriver) {
        ((JavascriptExecutor) webDriver).executeScript(
            "window.localStorage.removeItem('params');"
                + "window.localStorage.removeItem('$listparams');"
                + "window.localStorage.removeItem('wenshuListInfo');"
                + "window.localStorage.setItem('$listPageSearchItem', '{}');"
        );
    }

    private void waitForAppliedSearchConditions(WebDriver webDriver, Settings settings, SearchCriteria criteria, String expectedDateRange) {
        try {
            new WebDriverWait(webDriver, Duration.ofMillis(Math.max(settings.renderWaitMillis(), 3000L)))
                .until(driver -> hasExpectedDateFilter(driver, expectedDateRange)
                    && hasExpectedDocumentTypeFilter(driver, criteria)
                    && hasExpectedCaseTypeFilter(driver, criteria)
                    && (hasListItems(driver) || containsNoData(driver)));
        } catch (TimeoutException e) {
            RenderedPage renderedPage = capturePage(webDriver);
            Path snapshotPath = writeDebugSnapshot(renderedPage);
            throw new IllegalStateException(
                "高级检索提交后未在页面中观察到预期的裁判日期条件。"
                    + " expectedDateRange=" + expectedDateRange
                    + ", expectedDocumentType=" + criteria.documentTypeCode()
                    + ", expectedCaseType=" + criteria.caseTypeCode()
                    + ", actualFilters=" + activeFilterSignature(webDriver)
                    + ", currentUrl=" + renderedPage.currentUrl()
                    + ", title=" + renderedPage.title()
                    + ", snapshot=" + snapshotPath,
                e
            );
        }
    }

    private void verifyAppliedSearchConditions(WebDriver webDriver, SearchCriteria criteria, String expectedDateRange) {
        List<WebElement> filters = webDriver.findElements(By.cssSelector(".LT_Filter_right.clearfix p"));
        int expectedFilterCount = 1;
        if (StrUtil.isNotBlank(criteria.documentTypeCode())) {
            expectedFilterCount++;
        }
        if (StrUtil.isNotBlank(criteria.caseTypeCode())) {
            expectedFilterCount++;
        }
        boolean expectedFiltersApplied = filters.size() == expectedFilterCount
            && hasExpectedDateFilter(webDriver, expectedDateRange)
            && hasExpectedDocumentTypeFilter(webDriver, criteria);
        expectedFiltersApplied = expectedFiltersApplied && hasExpectedCaseTypeFilter(webDriver, criteria);
        if (expectedFiltersApplied) {
            return;
        }
        RenderedPage renderedPage = capturePage(webDriver);
        Path snapshotPath = writeDebugSnapshot(renderedPage);
        throw new IllegalStateException(
            "文书网当前生效的检索条件与预期不一致。"
                + " expectedFilters=cprq:" + expectedDateRange
                + (StrUtil.isBlank(criteria.documentTypeCode()) ? "" : ", s6:" + criteria.documentTypeCode())
                + (StrUtil.isBlank(criteria.caseTypeCode()) ? "" : ", s8:" + criteria.caseTypeCode())
                + ", actualFilters=" + activeFilterSignature(webDriver)
                + ", currentUrl=" + renderedPage.currentUrl()
                + ", title=" + renderedPage.title()
                + ", snapshot=" + snapshotPath
        );
    }

    private void ensureVisibleResultWindowComplete(int totalResultCount, String contextLabel) {
        if (totalResultCount <= 600) {
            return;
        }
        throw new IllegalStateException(
            "文书网当前检索结果共 " + totalResultCount + " 条，但页面最多只展示前 600 条。"
                + "请进一步缩小检索条件后重试。"
                + " context=" + contextLabel
        );
    }

    private String buildExpectedDateRange(String startValue, String endValue) {
        String start = StrUtil.blankToDefault(startValue, "1900-01-01");
        String end = StrUtil.blankToDefault(endValue, "2099-01-01");
        return start + " TO " + end;
    }

    private boolean hasExpectedDateFilter(WebDriver webDriver, String expectedDateRange) {
        List<WebElement> filters = webDriver.findElements(By.cssSelector(".LT_Filter_right.clearfix p[data-key='cprq']"));
        if (filters.isEmpty()) {
            return false;
        }
        String actual = StrUtil.trim(filters.get(0).getAttribute("data-value"));
        return StrUtil.equals(actual, expectedDateRange);
    }

    private boolean hasExpectedDocumentTypeFilter(WebDriver webDriver, SearchCriteria criteria) {
        if (StrUtil.isBlank(criteria.documentTypeCode())) {
            return webDriver.findElements(By.cssSelector(".LT_Filter_right.clearfix p[data-key='s6']")).isEmpty();
        }
        List<WebElement> filters = webDriver.findElements(By.cssSelector(".LT_Filter_right.clearfix p[data-key='s6']"));
        if (filters.isEmpty()) {
            return false;
        }
        WebElement filter = filters.get(0);
        String dataValue = StrUtil.trim(filter.getAttribute("data-value"));
        String text = StrUtil.trim(filter.getText());
        return StrUtil.equals(dataValue, criteria.documentTypeCode())
            || StrUtil.contains(text, criteria.documentTypeName());
    }

    private boolean hasExpectedCaseTypeFilter(WebDriver webDriver, SearchCriteria criteria) {
        if (StrUtil.isBlank(criteria.caseTypeCode())) {
            return webDriver.findElements(By.cssSelector(".LT_Filter_right.clearfix p[data-key='s8']")).isEmpty();
        }
        List<WebElement> filters = webDriver.findElements(By.cssSelector(".LT_Filter_right.clearfix p[data-key='s8']"));
        if (filters.isEmpty()) {
            return false;
        }
        WebElement filter = filters.get(0);
        String dataValue = StrUtil.trim(filter.getAttribute("data-value"));
        String text = StrUtil.trim(filter.getText());
        return StrUtil.equals(dataValue, criteria.caseTypeCode())
            || StrUtil.contains(text, criteria.caseTypeName());
    }

    private void setDocumentTypeCondition(WebDriver webDriver, SearchCriteria criteria) {
        String displayText = StrUtil.blankToDefault(criteria.documentTypeName(), StrUtil.EMPTY);
        List<WebElement> dropdowns = webDriver.findElements(By.cssSelector("#s6"));
        if (dropdowns.isEmpty()) {
            return;
        }
        WebElement dropdown = dropdowns.get(0);
        ((JavascriptExecutor) webDriver).executeScript(
            "arguments[0].setAttribute('data-val', arguments[1]);"
                + "arguments[0].setAttribute('data-value', arguments[1]);"
                + "arguments[0].textContent = arguments[2];",
            dropdown,
            StrUtil.nullToEmpty(criteria.documentTypeCode()),
            displayText
        );
        ((JavascriptExecutor) webDriver).executeScript(
            "document.querySelectorAll('#gjjs_wslx li').forEach(function(node){"
                + "var selected = node.getAttribute('data-val') === arguments[0];"
                + "if(selected){node.classList.add('on');}else{node.classList.remove('on');}"
                + "});",
            StrUtil.nullToEmpty(criteria.documentTypeCode())
        );
    }

    private void setCaseTypeCondition(WebDriver webDriver, SearchCriteria criteria) {
        String displayText = StrUtil.blankToDefault(criteria.caseTypeName(), StrUtil.EMPTY);
        List<WebElement> dropdowns = webDriver.findElements(By.cssSelector("#s8"));
        if (dropdowns.isEmpty()) {
            return;
        }
        WebElement dropdown = dropdowns.get(0);
        ((JavascriptExecutor) webDriver).executeScript(
            "arguments[0].setAttribute('data-val', arguments[1]);"
                + "arguments[0].setAttribute('data-value', arguments[1]);"
                + "arguments[0].textContent = arguments[2];",
            dropdown,
            StrUtil.nullToEmpty(criteria.caseTypeCode()),
            displayText
        );
        ((JavascriptExecutor) webDriver).executeScript(
            "document.querySelectorAll('#gjjs_ajlx li').forEach(function(node){"
                + "var selected = node.getAttribute('data-val') === arguments[0];"
                + "if(selected){node.classList.add('on');}else{node.classList.remove('on');}"
                + "});",
            StrUtil.nullToEmpty(criteria.caseTypeCode())
        );
    }

    private List<DocumentTypeOption> loadDocumentTypeOptions(WebDriver webDriver) {
        openAdvancedSearchIfNecessary(webDriver);
        List<DocumentTypeOption> options = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        for (WebElement option : webDriver.findElements(By.cssSelector("#gjjs_wslx li[data-val]"))) {
            String code = StrUtil.trim(option.getAttribute("data-val"));
            String name = StrUtil.trim(option.getText());
            if (StrUtil.isBlank(code) || !seenCodes.add(code)) {
                continue;
            }
            options.add(new DocumentTypeOption(code, StrUtil.blankToDefault(name, code)));
        }
        return options;
    }

    private List<CaseTypeOption> loadCaseTypeOptions(WebDriver webDriver) {
        openAdvancedSearchIfNecessary(webDriver);
        List<CaseTypeOption> options = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        for (WebElement option : webDriver.findElements(By.cssSelector("#gjjs_ajlx li[data-val]"))) {
            String code = StrUtil.trim(option.getAttribute("data-val"));
            String name = StrUtil.trim(option.getText());
            if (StrUtil.isBlank(code) || !seenCodes.add(code)) {
                continue;
            }
            options.add(new CaseTypeOption(code, StrUtil.blankToDefault(name, code)));
        }
        return options;
    }

    private String activeFilterSignature(WebDriver webDriver) {
        List<WebElement> filters = webDriver.findElements(By.cssSelector(".LT_Filter_right.clearfix p"));
        if (filters.isEmpty()) {
            return StrUtil.EMPTY;
        }
        List<String> values = new ArrayList<>();
        for (WebElement filter : filters) {
            values.add(StrUtil.format(
                "{}={}",
                StrUtil.nullToEmpty(filter.getAttribute("data-key")),
                StrUtil.nullToEmpty(filter.getAttribute("data-value"))
            ));
        }
        return String.join(", ", values);
    }

    private String firstVisibleDocId(WebDriver webDriver) {
        List<WebElement> elements = webDriver.findElements(By.cssSelector(".LM_list .list_title h4 a.caseName"));
        if (elements.isEmpty()) {
            return StrUtil.EMPTY;
        }
        return extractDocId(elements.get(0).getAttribute("href"));
    }

    private String selectedPageSize(WebDriver webDriver) {
        List<WebElement> pageSizeSelects = webDriver.findElements(By.cssSelector("select.pageSizeSelect"));
        if (pageSizeSelects.isEmpty()) {
            return StrUtil.EMPTY;
        }
        return StrUtil.trim(pageSizeSelects.get(0).getAttribute("value"));
    }

    private boolean hasPageChanged(WebDriver webDriver, String previousPageNumber, String previousFirstDocId) {
        String newPageNumber = currentPageNumber(webDriver);
        String newFirstDocId = firstVisibleDocId(webDriver);
        return (!StrUtil.isBlank(newPageNumber) && !StrUtil.equals(newPageNumber, previousPageNumber))
            || (!StrUtil.isBlank(newFirstDocId) && !StrUtil.equals(newFirstDocId, previousFirstDocId));
    }

    private String textOfFirst(WebDriver webDriver, String selector) {
        try {
            List<WebElement> elements = webDriver.findElements(By.cssSelector(selector));
            if (elements.isEmpty()) {
                return StrUtil.EMPTY;
            }
            return StrUtil.trim(elements.get(0).getText());
        } catch (StaleElementReferenceException e) {
            List<WebElement> elements = webDriver.findElements(By.cssSelector(selector));
            if (elements.isEmpty()) {
                return StrUtil.EMPTY;
            }
            return StrUtil.trim(elements.get(0).getText());
        }
    }

    private List<Map<String, Object>> parseRelatedDocuments(Element item) {
        List<Map<String, Object>> relatedDocuments = new ArrayList<>();
        for (Element anchor : item.select(".list_Association .guanLian a")) {
            Elements infos = anchor.select("i");
            Map<String, Object> related = new LinkedHashMap<>();
            String detailUrl = anchor.absUrl("href");
            related.put("detailUrl", detailUrl);
            related.put("docId", extractDocId(detailUrl));
            related.put("trialProcedure", text(infos, 0));
            related.put("court", text(infos, 1));
            related.put("caseNumber", text(infos, 2));
            related.put("judgmentDate", text(infos, 3));
            related.put("disclosureStatus", text(infos, 4));
            relatedDocuments.add(related);
        }
        return relatedDocuments;
    }

    private List<String> parseDetailSummaryLines(Document document) {
        List<String> summaryLines = new ArrayList<>();
        Element firstSummarySection = firstDetailSummarySection(document);
        if (firstSummarySection == null) {
            return summaryLines;
        }
        Element summaryListItem = firstChild(firstSummarySection.selectFirst("ul"), 0);
        if (summaryListItem == null) {
            return summaryLines;
        }
        for (Element summaryLine : summaryListItem.select("h4")) {
            String text = normalizeMultilineText(summaryLine.text());
            if (StrUtil.isNotBlank(text)) {
                summaryLines.add(text);
            }
        }
        return summaryLines;
    }

    private List<Map<String, Object>> parseDetailLegalBasis(Document document) {
        List<Map<String, Object>> legalBasis = new ArrayList<>();
        Element firstSummarySection = firstDetailSummarySection(document);
        if (firstSummarySection == null) {
            return legalBasis;
        }
        Element legalBasisItem = firstChild(firstSummarySection.selectFirst("ul"), 1);
        if (legalBasisItem == null) {
            return legalBasis;
        }
        String currentCategory = StrUtil.EMPTY;
        for (Element child : legalBasisItem.children()) {
            if ("h5".equalsIgnoreCase(child.tagName())) {
                currentCategory = normalizeMultilineText(child.text());
                continue;
            }
            if (!"p".equalsIgnoreCase(child.tagName())) {
                continue;
            }
            String currentItem = normalizeMultilineText(child.text());
            if (StrUtil.isBlank(currentItem)) {
                continue;
            }
            legalBasis.add(new LinkedHashMap<>(Map.of(
                "category", currentCategory,
                "item", currentItem
            )));
        }
        return legalBasis;
    }

    private List<Map<String, Object>> parseDetailRelatedDocuments(Document document) {
        List<Map<String, Object>> relatedDocuments = new ArrayList<>();
        for (Element anchor : document.select(".detail_guanlian .list_Association .guanLian a")) {
            Elements infos = anchor.select("i");
            Map<String, Object> related = new LinkedHashMap<>();
            String detailUrl = anchor.absUrl("href");
            related.put("detailUrl", detailUrl);
            related.put("docId", extractDocId(detailUrl));
            related.put("trialProcedure", text(infos, 0));
            related.put("court", text(infos, 1));
            related.put("caseNumber", text(infos, 2));
            related.put("judgmentDate", text(infos, 3));
            related.put("disclosureStatus", text(infos, 4));
            relatedDocuments.add(related);
        }
        return relatedDocuments;
    }

    private String summaryLine(DetailPage detailPage, int index) {
        if (detailPage.summaryLines().size() <= index) {
            return StrUtil.EMPTY;
        }
        return normalizeMultilineText(detailPage.summaryLines().get(index));
    }

    private String safeString(Object value) {
        return value == null ? StrUtil.EMPTY : normalizeMultilineText(String.valueOf(value));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return StrUtil.EMPTY;
    }

    private Object blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

    private String generateRecordId(String docId, String sourceUrl) {
        String seed = firstNonBlank(docId, sourceUrl, UUID.randomUUID().toString());
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private String extractDocType(String title) {
        if (StrUtil.isBlank(title)) {
            return StrUtil.EMPTY;
        }
        for (String suffix : DOC_TYPE_SUFFIXES) {
            if (StrUtil.endWith(title, suffix)) {
                return suffix;
            }
        }
        return StrUtil.EMPTY;
    }

    private String inferCaseType(String title, String trialProcedure) {
        String source = firstNonBlank(title, trialProcedure);
        if (StrUtil.contains(source, "民事")) {
            return "民事";
        }
        if (StrUtil.contains(source, "刑事")) {
            return "刑事";
        }
        if (StrUtil.contains(source, "行政")) {
            return "行政";
        }
        if (StrUtil.contains(source, "执行")) {
            return "执行";
        }
        if (StrUtil.contains(source, "赔偿")) {
            return "赔偿";
        }
        return StrUtil.EMPTY;
    }

    private String inferCause(String title) {
        if (StrUtil.isBlank(title)) {
            return StrUtil.EMPTY;
        }
        String normalizedTitle = title;
        for (String suffix : DOC_TYPE_SUFFIXES) {
            normalizedTitle = StrUtil.removeSuffix(normalizedTitle, suffix);
        }
        int lastCivil = normalizedTitle.lastIndexOf("民事");
        int lastCriminal = normalizedTitle.lastIndexOf("刑事");
        int lastAdministrative = normalizedTitle.lastIndexOf("行政");
        int lastExecution = normalizedTitle.lastIndexOf("执行");
        int splitIndex = Math.max(Math.max(lastCivil, lastCriminal), Math.max(lastAdministrative, lastExecution));
        if (splitIndex > 0) {
            return StrUtil.subSuf(normalizedTitle, normalizedTitle.lastIndexOf(';') + 1).trim();
        }
        return normalizedTitle;
    }

    private String normalizeDateText(String text) {
        if (StrUtil.isBlank(text)) {
            return StrUtil.EMPTY;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return StrUtil.EMPTY;
        }
        return String.format("%s-%02d-%02d", matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    private String extractAcceptDate(String content) {
        if (StrUtil.isBlank(content)) {
            return StrUtil.EMPTY;
        }
        Matcher matcher = ACCEPT_DATE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return StrUtil.EMPTY;
        }
        return normalizeDateText(matcher.group(1));
    }

    private Integer extractJudgeYear(String judgeDate, String caseId) {
        String normalizedJudgeDate = normalizeDateText(judgeDate);
        if (StrUtil.isNotBlank(normalizedJudgeDate)) {
            return Integer.parseInt(StrUtil.subPre(normalizedJudgeDate, 4));
        }
        Matcher matcher = CASE_YEAR_PATTERN.matcher(StrUtil.nullToEmpty(caseId));
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String inferCourtLevel(String court) {
        if (StrUtil.isBlank(court)) {
            return StrUtil.EMPTY;
        }
        if (StrUtil.contains(court, "最高人民法院")) {
            return "最高法院";
        }
        if (StrUtil.contains(court, "高级人民法院") || StrUtil.contains(court, "高级法院")) {
            return "高级法院";
        }
        if (StrUtil.contains(court, "中级人民法院") || StrUtil.contains(court, "铁路运输中级法院")) {
            return "中级法院";
        }
        if (StrUtil.contains(court, "互联网法院")) {
            return "专门法院";
        }
        if (StrUtil.containsAny(court, "基层人民法院", "区人民法院", "县人民法院", "旗人民法院", "市人民法院")) {
            return "基层法院";
        }
        return StrUtil.EMPTY;
    }

    private CourtLocation parseCourtLocation(String court) {
        if (StrUtil.isBlank(court)) {
            return new CourtLocation(StrUtil.EMPTY, StrUtil.EMPTY, StrUtil.EMPTY);
        }
        String province = StrUtil.EMPTY;
        for (String provinceName : PROVINCE_NAMES) {
            if (StrUtil.startWith(court, provinceName)) {
                province = provinceName;
                break;
            }
        }
        if (StrUtil.isBlank(province) && StrUtil.startWith(court, "新疆生产建设兵团")) {
            province = "新疆生产建设兵团";
        }
        String remaining = StrUtil.removePrefix(court, province);
        String city = extractRegionToken(remaining, "市", "地区", "自治州", "盟");
        if (StrUtil.isNotBlank(city)) {
            remaining = StrUtil.removePrefix(remaining, city);
        }
        String region = extractRegionToken(remaining, "区", "县", "旗", "市");
        return new CourtLocation(province, city, region);
    }

    private String extractRegionToken(String text, String... suffixes) {
        if (StrUtil.isBlank(text)) {
            return StrUtil.EMPTY;
        }
        int bestIndex = Integer.MAX_VALUE;
        String bestSuffix = null;
        for (String suffix : suffixes) {
            int index = text.indexOf(suffix);
            if (index >= 0 && index < bestIndex) {
                bestIndex = index;
                bestSuffix = suffix;
            }
        }
        if (bestSuffix == null) {
            return StrUtil.EMPTY;
        }
        return StrUtil.sub(text, 0, bestIndex + bestSuffix.length());
    }

    private String buildLawBasisText(List<Map<String, Object>> legalBasis) {
        if (legalBasis == null || legalBasis.isEmpty()) {
            return StrUtil.EMPTY;
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : legalBasis) {
            String category = normalizeMultilineText(String.valueOf(item.getOrDefault("category", StrUtil.EMPTY)));
            String lawItem = normalizeMultilineText(String.valueOf(item.getOrDefault("item", StrUtil.EMPTY)));
            if (StrUtil.isBlank(category) && StrUtil.isBlank(lawItem)) {
                continue;
            }
            lines.add(StrUtil.isBlank(category) ? lawItem : category + "," + lawItem);
        }
        return String.join("\n", lines);
    }

    private String buildFullContent(DetailPage detailPage) {
        String title = detailPage.documentTitle();
        String content = detailPage.documentContent();
        if (StrUtil.isBlank(title)) {
            return content;
        }
        if (StrUtil.startWith(content, title)) {
            return content;
        }
        return title + "\n" + content;
    }

    private String extractResult(String content) {
        List<String> lines = splitContentLines(content);
        int markerIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (StrUtil.containsAny(line, "判决如下", "裁定如下", "决定如下", "通知如下", "判定如下", "如下裁定", "如下判决")) {
                markerIndex = i;
                break;
            }
        }
        if (markerIndex < 0) {
            return StrUtil.EMPTY;
        }
        List<String> resultLines = new ArrayList<>();
        for (int i = markerIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (StrUtil.isBlank(line)) {
                if (!resultLines.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!resultLines.isEmpty() && StrUtil.startWithAny(line, "案件受理费", "审判长", "审判员", "书记员", "法官助理", "执行员", "附相关法条", "附：", "附录")) {
                break;
            }
            if (!resultLines.isEmpty() && StrUtil.startWithAny(line, "二〇", "二○", "二Ｏ", "20")) {
                break;
            }
            resultLines.add(line);
            if (resultLines.size() >= 3) {
                break;
            }
        }
        return String.join("\n", resultLines);
    }

    private String extractCaseIdFromContent(String content) {
        for (String line : splitContentLines(content)) {
            if (CASE_YEAR_PATTERN.matcher(line).find() && StrUtil.endWith(line, "号")) {
                return line;
            }
        }
        return StrUtil.EMPTY;
    }

    private List<Map<String, Object>> extractParties(String content) {
        List<Map<String, Object>> parties = new ArrayList<>();
        for (String line : splitContentLines(content)) {
            String normalizedLine = normalizeMultilineText(line);
            if (StrUtil.isBlank(normalizedLine)) {
                continue;
            }
            int separatorIndex = normalizedLine.indexOf('：');
            if (separatorIndex < 0) {
                separatorIndex = normalizedLine.indexOf(':');
            }
            if (separatorIndex <= 0) {
                continue;
            }
            String role = StrUtil.sub(normalizedLine, 0, separatorIndex).trim();
            if (!isPartyRole(role)) {
                continue;
            }
            String description = StrUtil.subSuf(normalizedLine, separatorIndex + 1).trim();
            Map<String, Object> party = new LinkedHashMap<>();
            party.put("role", role);
            party.put("name", extractPartyName(description));
            party.put("content", description);
            parties.add(party);
        }
        return parties;
    }

    private boolean isPartyRole(String role) {
        return Arrays.asList(
            "原告", "被告", "上诉人", "被上诉人", "申请人", "被申请人", "再审申请人", "再审被申请人",
            "申请执行人", "被执行人", "第三人", "原审原告", "原审被告", "原审上诉人", "原审被上诉人",
            "公诉机关", "自诉人", "被告人", "附带民事诉讼原告人", "附带民事诉讼被告人",
            "法定代表人", "负责人", "法定代理人", "委托诉讼代理人", "委托代理人", "辩护人"
        ).contains(role);
    }

    private String extractPartyName(String description) {
        if (StrUtil.isBlank(description)) {
            return StrUtil.EMPTY;
        }
        for (char separator : new char[]{'，', ',', '。', '；', ';'}) {
            int index = description.indexOf(separator);
            if (index > 0) {
                return StrUtil.sub(description, 0, index).trim();
            }
        }
        return description.trim();
    }

    private List<String> splitContentLines(String content) {
        if (StrUtil.isBlank(content)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : normalizeMultilineText(content).split("\\n")) {
            String line = rawLine.trim();
            if (StrUtil.isNotBlank(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    private Element firstDetailSummarySection(Document document) {
        Elements sections = document.select(".del_right .gaiyao_center");
        if (sections.isEmpty()) {
            return null;
        }
        return sections.get(0);
    }

    private Element firstChild(Element parent, int index) {
        if (parent == null || parent.childrenSize() <= index) {
            return null;
        }
        return parent.child(index);
    }

    private boolean matchesExpectedKeyword(String expectedKeyword, String title, String caseNumber, String reasonSummary) {
        if (StrUtil.isBlank(expectedKeyword)) {
            return true;
        }
        return StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(title), expectedKeyword)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(caseNumber), expectedKeyword)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(reasonSummary), expectedKeyword);
    }

    private void openAdvancedSearchIfNecessary(WebDriver webDriver) {
        try {
            List<WebElement> triggers = webDriver.findElements(By.cssSelector(".advenced-search"));
            if (!triggers.isEmpty()) {
                triggers.get(0).click();
            }
        } catch (RuntimeException ignored) {
            // 高级检索面板在部分状态下可能已展开，忽略点击失败。
        }
    }

    private boolean setInputValue(WebDriver webDriver, String selector, String value) {
        if (StrUtil.isBlank(value)) {
            return true;
        }
        List<WebElement> elements = webDriver.findElements(By.cssSelector(selector));
        if (elements.isEmpty()) {
            return false;
        }
        WebElement element = elements.get(0);
        ((JavascriptExecutor) webDriver).executeScript(
            "arguments[0].value = arguments[1];"
                + "arguments[0].dispatchEvent(new Event('input', {bubbles:true}));"
                + "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
            element,
            value
        );
        return true;
    }

    private boolean hasAdvancedDateInputs(WebDriver webDriver) {
        return !webDriver.findElements(By.cssSelector("#cprqStart")).isEmpty()
            && !webDriver.findElements(By.cssSelector("#cprqEnd")).isEmpty();
    }

    private boolean containsNoData(WebDriver webDriver) {
        return StrUtil.contains(capturePage(webDriver).html(), "暂无数据");
    }

    private boolean hasListItems(WebDriver webDriver) {
        return !webDriver.findElements(By.cssSelector(".LM_list")).isEmpty();
    }

    private int countVisibleListItems(WebDriver webDriver) {
        return webDriver.findElements(By.cssSelector(".LM_list")).size();
    }

    private int totalResultCount(WebDriver webDriver) {
        String countText = textOfFirst(webDriver, ".fr.con_right span");
        if (StrUtil.isBlank(countText)) {
            return 0;
        }
        try {
            return Integer.parseInt(countText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractDocId(String url) {
        if (StrUtil.isBlank(url)) {
            return StrUtil.EMPTY;
        }
        try {
            String query = URI.create(url).getQuery();
            if (StrUtil.isBlank(query)) {
                return StrUtil.EMPTY;
            }
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2 && "docId".equals(parts[0])) {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
        } catch (RuntimeException ignored) {
            return StrUtil.EMPTY;
        }
        return StrUtil.EMPTY;
    }

    private String text(Element element) {
        return element == null ? StrUtil.EMPTY : StrUtil.trim(element.text());
    }

    private String wholeText(Element element) {
        return element == null ? StrUtil.EMPTY : StrUtil.trim(element.wholeText());
    }

    private String text(Elements elements, int index) {
        if (elements == null || elements.size() <= index) {
            return StrUtil.EMPTY;
        }
        return StrUtil.trim(elements.get(index).text());
    }

    private long readLong(String key, long defaultValue) {
        String value = readValue(key);
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long value for " + key + ": " + value, e);
        }
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        String value = readValue(key);
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim())
            || "1".equalsIgnoreCase(value.trim())
            || "yes".equalsIgnoreCase(value.trim())
            || "y".equalsIgnoreCase(value.trim());
    }

    private String normalizeMultilineText(String text) {
        if (StrUtil.isBlank(text)) {
            return StrUtil.EMPTY;
        }
        return text
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\t\\x0B\\f]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private String summarizeExceptionMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return StrUtil.blankToDefault(StrUtil.trim(current.getMessage()), throwable.getClass().getSimpleName());
    }

    private String readValue(String key) {
        String systemValue = System.getProperty(key);
        if (StrUtil.isNotBlank(systemValue)) {
            return systemValue.trim();
        }
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        if (StrUtil.isNotBlank(envValue)) {
            return envValue.trim();
        }
        return StrUtil.EMPTY;
    }

    private boolean isLoginPage(RenderedPage renderedPage, Settings settings) {
        return StrUtil.containsIgnoreCase(renderedPage.currentUrl(), "open=login")
            || StrUtil.containsIgnoreCase(renderedPage.title(), "登录")
            || StrUtil.containsIgnoreCase(renderedPage.html(), settings.loginPageKeyword())
            || StrUtil.containsIgnoreCase(renderedPage.html(), "id=\"contentIframe\"")
            || StrUtil.containsIgnoreCase(renderedPage.html(), "account.court.gov.cn/oauth/authorize");
    }

    private RenderedPage capturePage(WebDriver webDriver) {
        return new RenderedPage(
            StrUtil.nullToEmpty(webDriver.getPageSource()),
            StrUtil.nullToEmpty(webDriver.getCurrentUrl()),
            StrUtil.nullToEmpty(webDriver.getTitle())
        );
    }

    private Path writeDebugSnapshot(RenderedPage renderedPage) {
        try {
            Path debugDir = SpiderPaths.resolveDataDirectory().resolve("debug");
            Files.createDirectories(debugDir);
            Path snapshotPath = debugDir.resolve(NAME + "-last-page.html");
            Files.writeString(snapshotPath, renderedPage.html(), StandardCharsets.UTF_8);
            return snapshotPath;
        } catch (Exception e) {
            throw new IllegalStateException("写入文书网调试快照失败", e);
        }
    }

    private record Settings(
        String targetUrl,
        String browserUserDataDir,
        String targetJudgmentDateStart,
        String targetJudgmentDateEnd,
        String expectedKeyword,
        String loginPageKeyword,
        long renderWaitMillis,
        long detailRenderWaitMillis,
        long manualLoginTimeoutMillis,
        long judgmentDateOffsetDays,
        boolean extractDetailEnabled
    ) {

        private static Settings load(WenshuCourtSpider spider) {
            long judgmentDateOffsetDays = spider.readLong(PROPERTY_PREFIX + "judgment-date-offset-days", DEFAULT_JUDGMENT_DATE_OFFSET_DAYS);
            String targetJudgmentDateStart = StrUtil.blankToDefault(
                spider.readValue(PROPERTY_PREFIX + "target-judgment-date-start"),
                LocalDate.now().minusDays(Math.max(judgmentDateOffsetDays, 0L)).toString()
            );
            String targetJudgmentDateEnd = StrUtil.blankToDefault(
                spider.readValue(PROPERTY_PREFIX + "target-judgment-date-end"),
                targetJudgmentDateStart
            );
            targetJudgmentDateEnd = targetJudgmentDateStart;
            String targetUrl = refreshPageId(StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "target-url"), DEFAULT_TARGET_URL));
            return new Settings(
                targetUrl,
                StrUtil.blankToDefault(
                    spider.readValue(PROPERTY_PREFIX + "browser-user-data-dir"),
                    SpiderPaths.resolveDataDirectory().resolve("browser").resolve(NAME).toString()
                ),
                targetJudgmentDateStart,
                targetJudgmentDateEnd,
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "expected-keyword"), DEFAULT_EXPECTED_KEYWORD),
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "login-page-keyword"), DEFAULT_LOGIN_PAGE_KEYWORD),
                spider.readLong(PROPERTY_PREFIX + "render-wait-millis", DEFAULT_RENDER_WAIT_MILLIS),
                spider.readLong(PROPERTY_PREFIX + "detail-render-wait-millis", DEFAULT_DETAIL_RENDER_WAIT_MILLIS),
                spider.readLong(PROPERTY_PREFIX + "manual-login-timeout-millis", DEFAULT_MANUAL_LOGIN_TIMEOUT_MILLIS),
                judgmentDateOffsetDays,
                spider.readBoolean(PROPERTY_PREFIX + "extract-detail-enabled", DEFAULT_EXTRACT_DETAIL_ENABLED)
            );
        }

        private static String refreshPageId(String targetUrl) {
            String freshPageId = UUID.randomUUID().toString();
            if (StrUtil.containsIgnoreCase(targetUrl, "pageId=")) {
                return targetUrl.replaceAll("([?&])pageId=[^&#]*", "$1pageId=" + freshPageId);
            }
            String separator = StrUtil.contains(targetUrl, "?") ? "&" : "?";
            return targetUrl + separator + "pageId=" + freshPageId;
        }
    }

    private record ParsedPage(List<CrawlResult> items, boolean keywordMatched) {
    }

    private record SearchCriteria(
        String judgmentDateStart,
        String judgmentDateEnd,
        String documentTypeCode,
        String documentTypeName,
        String caseTypeCode,
        String caseTypeName
    ) {
        private static SearchCriteria singleDay(String judgmentDateStart, String judgmentDateEnd) {
            String day = StrUtil.blankToDefault(judgmentDateStart, judgmentDateEnd);
            return new SearchCriteria(day, day, StrUtil.EMPTY, StrUtil.EMPTY, StrUtil.EMPTY, StrUtil.EMPTY);
        }

        private SearchCriteria withDocumentType(String code, String name) {
            return new SearchCriteria(this.judgmentDateStart, this.judgmentDateEnd, code, name, StrUtil.EMPTY, StrUtil.EMPTY);
        }

        private SearchCriteria withCaseType(String code, String name) {
            return new SearchCriteria(this.judgmentDateStart, this.judgmentDateEnd, this.documentTypeCode, this.documentTypeName, code, name);
        }
    }

    private record DocumentTypeOption(String code, String name) {
    }

    private record CaseTypeOption(String code, String name) {
    }

    private record DetailPage(
        String currentUrl,
        String pageTitle,
        String documentTitle,
        String documentContent,
        String caseLevel,
        List<String> summaryLines,
        List<Map<String, Object>> legalBasis,
        List<Map<String, Object>> relatedDocuments
    ) {
    }

    private record CourtLocation(String province, String city, String region) {
    }

    private record RenderedPage(String html, String currentUrl, String title) {
    }
}
