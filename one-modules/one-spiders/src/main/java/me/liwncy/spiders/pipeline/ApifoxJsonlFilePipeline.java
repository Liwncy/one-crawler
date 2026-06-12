package me.liwncy.spiders.pipeline;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.liwncy.common.crawler.config.CrawlerProperties;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.pipeline.SpiderPipeline;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将爬虫结果导出为 Apifox 可导入的 JSONL 文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApifoxJsonlFilePipeline implements SpiderPipeline {

    private final CrawlerProperties crawlerProperties;

    @Override
    public String getName() {
        return "apifox-jsonl";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(CrawlResult result, SpiderConfig config) {
        try {
            Path basePath = Path.of(crawlerProperties.getOutputPath());
            Files.createDirectories(basePath);
            Path filePath = basePath.resolve(config.getName() + "-apifox.jsonl");
            Map<String, Object> htmlInfo = (Map<String, Object>) result.get("html_info");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", result.get("keyword"));
            payload.put("url", result.get("url"));
            payload.put("method", htmlInfo == null ? null : htmlInfo.get("request_method"));
            payload.put("mode", result.get("mode"));
            payload.put("fileType", result.get("fileType"));
            payload.put("jsonPath", result.get("jsonPath"));
            payload.put("htmlInfo", htmlInfo);
            payload.put("tags", List.of(config.getName()));
            Files.writeString(
                filePath,
                JSONUtil.toJsonStr(payload) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.error("write apifox jsonl file failed, spider={}", config.getName(), e);
            throw new IllegalStateException("write apifox jsonl file failed", e);
        }
    }
}
