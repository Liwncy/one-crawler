package me.liwncy.spiders.task;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import me.liwncy.common.crawler.core.AbstractSpider;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.core.SpiderFramework;
import me.liwncy.common.crawler.core.SpiderParseResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PearAPI 接口文档抓取任务。
 */
@Component
public class PearApiSpider extends AbstractSpider {

    public static final String NAME = "pear-api";

    private static final String API_BASE = "https://api.pearapi.ai/system";
    private static final String LIST_URL_PREFIX = API_BASE + "/auth/api/list?page=";
    private static final String INFO_URL_PREFIX = API_BASE + "/auth/api/info/?id=";
    private static final int PAGE_SIZE = 52;

    private static final Map<Integer, String> REQUEST_METHODS = Map.of(
        0, "GET",
        1, "POST",
        2, "POST",
        3, "GET",
        4, "POST"
    );

    private static final Map<Integer, String> RETURN_FORMATS = Map.of(
        0, "json",
        1, "text",
        2, "media"
    );

    @Override
    public SpiderConfig getConfig() {
        return SpiderConfig.builder()
            .name(NAME)
            .description("抓取 PearAPI 接口列表并导出为 Apifox 可用结构")
            .framework(SpiderFramework.WEBMAGIC)
            .startUrls(List.of(LIST_URL_PREFIX + "1&pageSize=" + PAGE_SIZE))
            .threadCount(3)
            .pipelines(List.of("apifox-jsonl", "swagger-jsonl"))
            .build();
    }

    @Override
    public SpiderParseResult parse(String htmlText, String url) {
        if (StrUtil.isBlank(htmlText)) {
            return SpiderParseResult.empty();
        }
        JSONObject root = JSONUtil.parseObj(htmlText);
        if (root.getInt("code", -1) != 200) {
            return SpiderParseResult.empty();
        }
        if (url.contains("/auth/api/list")) {
            return parseListPage(root, url);
        }
        if (url.contains("/auth/api/info")) {
            return parseDetailPage(root, url);
        }
        return SpiderParseResult.empty();
    }

    private SpiderParseResult parseListPage(JSONObject root, String url) {
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            return SpiderParseResult.empty();
        }

        SpiderParseResult result = SpiderParseResult.empty();
        for (Object item : data.getJSONArray("list")) {
            JSONObject api = JSONUtil.parseObj(item);
            Integer id = api.getInt("id");
            if (id != null) {
                result.addTargetUrl(INFO_URL_PREFIX + id);
            }
        }

        JSONObject pagination = data.getJSONObject("pagination");
        if (pagination != null && Boolean.TRUE.equals(pagination.getBool("has_more"))) {
            int nextPage = pagination.getInt("current_page", extractPage(url)) + 1;
            result.addTargetUrl(LIST_URL_PREFIX + nextPage + "&pageSize=" + PAGE_SIZE);
        }
        return result;
    }

    private SpiderParseResult parseDetailPage(JSONObject root, String url) {
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            return SpiderParseResult.empty();
        }

        String requestUrl = data.getStr("url");
        String returnFormat = RETURN_FORMATS.getOrDefault(data.getInt("return_type", 0), "json");
        Map<String, Object> htmlInfo = new LinkedHashMap<>();
        htmlInfo.put("title", data.getStr("name"));
        htmlInfo.put("title_en", data.getStr("name_en"));
        htmlInfo.put("description", data.getStr("info"));
        htmlInfo.put("description_en", data.getStr("info_en"));
        htmlInfo.put("request_address", requestUrl);
        htmlInfo.put("request_method", REQUEST_METHODS.getOrDefault(data.getInt("type", 0), "GET"));
        htmlInfo.put("request_params", formatRequestParams(data.get("request")));
        htmlInfo.put("response_params", formatResponseParams(data.get("result")));
        htmlInfo.put("return_format", returnFormat);
        htmlInfo.put("return_example", JSONUtil.toJsonStr(data.get("example_result")));
        htmlInfo.put("price", data.getStr("price"));
        htmlInfo.put("price_type", data.getStr("price_type"));
        htmlInfo.put("category_id", data.get("type_id"));
        htmlInfo.put("source_id", data.get("id"));
        htmlInfo.put("source_url", url);

        CrawlResult crawlResult = CrawlResult.builder().build()
            .field("id", url)
            .field("html_info", htmlInfo)
            .field("keyword", data.getStr("name"))
            .field("url", requestUrl)
            .field("mode", returnFormat)
            .field("jsonPath", "$")
            .field("fileType", returnFormat);

        return SpiderParseResult.empty().addItem(crawlResult);
    }

    private int extractPage(String url) {
        String pageToken = "page=";
        int start = url.indexOf(pageToken);
        if (start < 0) {
            return 1;
        }
        start += pageToken.length();
        int end = url.indexOf('&', start);
        String pageValue = end < 0 ? url.substring(start) : url.substring(start, end);
        return Integer.parseInt(pageValue);
    }

    private String formatRequestParams(Object request) {
        if (request == null) {
            return StrUtil.EMPTY;
        }
        if (request instanceof cn.hutool.json.JSONArray array) {
            return array.isEmpty() ? StrUtil.EMPTY : JSONUtil.toJsonPrettyStr(array);
        }
        JSONObject requestObject = JSONUtil.parseObj(request);
        if (requestObject.isEmpty()) {
            return StrUtil.EMPTY;
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Object> entry : requestObject.entrySet()) {
            if (entry.getValue() instanceof cn.hutool.json.JSONArray) {
                lines.add(entry.getKey() + ": " + JSONUtil.toJsonStr(entry.getValue()));
                continue;
            }
            JSONObject param = JSONUtil.parseObj(entry.getValue());
            String required = param.getInt("must", 0) == 1 ? "必填" : "可选";
            lines.add(String.format("%s (%s): %s", entry.getKey(), required, param.getStr("details")));
        }
        return String.join(System.lineSeparator(), lines);
    }

    private String formatResponseParams(Object result) {
        if (result == null) {
            return StrUtil.EMPTY;
        }
        if (result instanceof String text) {
            return text;
        }
        if (result instanceof cn.hutool.json.JSONArray array) {
            return array.isEmpty() ? StrUtil.EMPTY : JSONUtil.toJsonPrettyStr(array);
        }
        JSONObject resultObject = JSONUtil.parseObj(result);
        if (resultObject.isEmpty()) {
            return StrUtil.EMPTY;
        }
        return JSONUtil.toJsonPrettyStr(resultObject);
    }
}
