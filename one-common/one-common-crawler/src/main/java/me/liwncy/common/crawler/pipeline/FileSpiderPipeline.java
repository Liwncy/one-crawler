package me.liwncy.common.crawler.pipeline;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.liwncy.common.crawler.config.CrawlerProperties;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 通用文件输出管道，按 json line 形式写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileSpiderPipeline implements SpiderPipeline {

    private final CrawlerProperties crawlerProperties;

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public void process(CrawlResult result, SpiderConfig config) {
        try {
            Path basePath = Path.of(crawlerProperties.getOutputPath());
            Files.createDirectories(basePath);
            Path filePath = basePath.resolve(config.getName() + ".jsonl");
            Files.writeString(
                filePath,
                JSONUtil.toJsonStr(result.getFields()) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.error("write spider result failed, spider={}", config.getName(), e);
            throw new IllegalStateException("write spider result failed", e);
        }
    }
}

