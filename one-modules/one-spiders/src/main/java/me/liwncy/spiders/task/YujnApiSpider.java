package me.liwncy.spiders.task;

import cn.hutool.core.util.StrUtil;
import me.liwncy.common.crawler.core.AbstractSpider;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.core.SpiderFramework;
import me.liwncy.common.crawler.core.SpiderParseResult;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.selector.Html;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 遇见 API 文档抓取任务。
 */
@Component
public class YujnApiSpider extends AbstractSpider {

    public static final String NAME = "yujn-api";

    private static final String ROOT_URL = "https://api.yujn.cn";

    @Override
    public SpiderConfig getConfig() {
        return SpiderConfig.builder()
            .name(NAME)
            .description("抓取遇见 API 文档并导出为 Apifox 可用结构")
            .framework(SpiderFramework.WEBMAGIC)
            .startUrls(List.of(ROOT_URL))
            .threadCount(5)
            .pipelines(List.of("apifox-jsonl", "swagger-jsonl"))
            .build();
    }

    @Override
    public SpiderParseResult parse(String htmlText, String url) {
        Html html = new Html(htmlText, url);
        SpiderParseResult result = SpiderParseResult.empty();
        if (ROOT_URL.equals(url)) {
            List<String> onclickUrls = html.xpath("//div[@class='mdui-container mdui-col']//button/@onclick").all();
            for (String onclickUrl : onclickUrls) {
                if (StrUtil.isBlank(onclickUrl)) {
                    continue;
                }
                String subPath = StrUtil.subBetween(onclickUrl, "'", "'");
                if (StrUtil.isNotBlank(subPath)) {
                    result.addTargetUrl(ROOT_URL + "/" + subPath);
                }
            }
            return result;
        }

        List<String> requestLinks = html.xpath("//div[@class='mdui-typo']//p[@id='rest_url']//a/@href").all();
        Map<String, Object> htmlInfo = new LinkedHashMap<>();
        htmlInfo.put("title", html.xpath("//a[@class='mdui-typo-title']/text()").get());
        htmlInfo.put("description", html.xpath("//div[@class='mdui-text-color-white-text mdui-valign mdui-color-theme-accent']//font/text()").get());
        htmlInfo.put("request_address", getListValue(requestLinks, 0));
        htmlInfo.put("request_example", getListValue(requestLinks, 1));
        htmlInfo.put("request_method", html.xpath("//div[@class='mdui-typo']//p//kbd/text()").get());
        htmlInfo.put("request_params", html.xpath("//div[@class='mdui-typo']//table/text()").get());
        htmlInfo.put("return_format", html.xpath("//div[@class='mdui-typo']//p//code/text()").get());
        htmlInfo.put("return_example", html.xpath("//div[@class='mdui-typo']//pre//code/text()").get());

        CrawlResult crawlResult = CrawlResult.builder().build()
            .field("id", url)
            .field("html_info", htmlInfo)
            .field("keyword", htmlInfo.get("title"))
            .field("url", htmlInfo.get("request_address"))
            .field("mode", StrUtil.nullToDefault((String) htmlInfo.get("return_format"), "json").toLowerCase())
            .field("jsonPath", "$")
            .field("fileType", StrUtil.nullToDefault((String) htmlInfo.get("return_format"), "json").toLowerCase());

        return result.addItem(crawlResult);
    }

    private String getListValue(List<String> values, int index) {
        if (values == null || values.size() <= index) {
            return StrUtil.EMPTY;
        }
        return values.get(index);
    }
}


