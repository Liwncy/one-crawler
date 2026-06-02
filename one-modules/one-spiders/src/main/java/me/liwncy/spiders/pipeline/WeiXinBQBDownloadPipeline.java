package me.liwncy.spiders.pipeline;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.liwncy.common.crawler.config.CrawlerProperties;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.pipeline.SpiderPipeline;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 微信表情包下载管道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeiXinBQBDownloadPipeline implements SpiderPipeline {

    private final CrawlerProperties crawlerProperties;

    @Override
    public String getName() {
        return "weixin-bqb-download";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(CrawlResult result, SpiderConfig config) {
        List<String> picList = (List<String>) result.get("picList");
        if (picList == null || picList.isEmpty()) {
            return;
        }

        String title = StrUtil.nullToDefault((String) result.get("title"), config.getName());
        Path outputDir = Path.of(crawlerProperties.getOutputPath(), "weixin", sanitizeFileName(title));

        try {
            Files.createDirectories(outputDir);
            for (int index = 0; index < picList.size(); index++) {
                String picUrl = picList.get(index);
                if (StrUtil.isBlank(picUrl)) {
                    continue;
                }
                String extension = resolveExtension(picUrl);
                String fileName = String.format("%03d%s", index + 1, extension);
                Path targetPath = outputDir.resolve(fileName);
                download(picUrl, targetPath);
            }
        } catch (IOException e) {
            log.error("download weixin bqb failed, spider={}, title={}", config.getName(), title, e);
            throw new IllegalStateException("download weixin bqb failed", e);
        }
    }

    private void download(String picUrl, Path targetPath) throws IOException {
        try (InputStream inputStream = URI.create(picUrl).toURL().openStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sanitizeFileName(String fileName) {
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return StrUtil.blankToDefault(sanitized, "weixin-bqb");
    }

    private String resolveExtension(String picUrl) {
        try {
            String path = new URI(picUrl).getPath();
            if (StrUtil.isBlank(path) || !path.contains(".")) {
                return ".jpg";
            }
            String extension = path.substring(path.lastIndexOf('.'));
            if (extension.length() > 10 || extension.contains("/")) {
                return ".jpg";
            }
            return extension;
        } catch (IllegalArgumentException | URISyntaxException e) {
            log.warn("resolve weixin picture extension failed, url={}", picUrl, e);
            return ".jpg";
        }
    }
}

