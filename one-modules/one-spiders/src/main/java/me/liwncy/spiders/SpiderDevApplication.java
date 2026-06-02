package me.liwncy.spiders;

import me.liwncy.adapter.webmagic.WebMagicAdapter;
import me.liwncy.adapter.webmagic.config.WebMagicConfig;
import me.liwncy.adapter.webmagic.config.WebMagicProperties;
import me.liwncy.common.crawler.config.CrawlerProperties;
import me.liwncy.common.crawler.core.AbstractSpider;
import me.liwncy.common.crawler.core.CrawlerEngine;
import me.liwncy.common.crawler.pipeline.ConsoleSpiderPipeline;
import me.liwncy.common.crawler.pipeline.FileSpiderPipeline;
import me.liwncy.common.crawler.pipeline.SpiderPipeline;
import me.liwncy.spiders.pipeline.WeiXinBQBDownloadPipeline;
import me.liwncy.spiders.pipeline.YujnApiApiFoxFilePipeline;
import me.liwncy.spiders.task.TestSpider;
import me.liwncy.spiders.task.WeiXinBQBSpider;
import me.liwncy.spiders.task.YujnApiSpider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spider 开发阶段本地启动入口。
 * <p>
 * 使用方式与代码生成器类似：
 * 1. 直接修改下面的常量，手动切换目标 spider；
 * 2. 也可以通过 main 参数覆盖：第一个参数是 spiderName，第二个参数是输出目录。
 */
public class SpiderDevApplication {

    /**
     * 默认要运行的 spider，可在开发时手动切换。
     */
    private static final String DEFAULT_SPIDER_NAME = YujnApiSpider.NAME;

    /**
     * 默认输出目录名称。
     */
    private static final String DEFAULT_OUTPUT_DIR_NAME = "_out/test";

    public static void main(String[] args) {
        Map<String, AbstractSpider> spiderRegistry = createSpiderRegistry();
        String spiderName = resolveSpiderName(args);
        String outputPath = resolveOutputPath(args);

        if (!spiderRegistry.containsKey(spiderName)) {
            System.err.println("Unknown spider: " + spiderName);
            System.err.println("Available spiders: " + String.join(", ", spiderRegistry.keySet()));
            throw new IllegalArgumentException("Unknown spider: " + spiderName);
        }

        CrawlerProperties crawlerProperties = new CrawlerProperties();
        crawlerProperties.setOutputPath(outputPath);

        WebMagicProperties webMagicProperties = new WebMagicProperties();
        WebMagicConfig webMagicConfig = new WebMagicConfig();
        List<SpiderPipeline> pipelines = List.of(
            new ConsoleSpiderPipeline(),
            new FileSpiderPipeline(crawlerProperties),
            new WeiXinBQBDownloadPipeline(crawlerProperties),
            new YujnApiApiFoxFilePipeline(crawlerProperties)
        );

        CrawlerEngine crawlerEngine = new CrawlerEngine(
            List.of(new WebMagicAdapter(webMagicConfig.webMagicSite(webMagicProperties), pipelines)),
            List.copyOf(spiderRegistry.values())
        );

        System.out.println("Spider dev run start: " + spiderName);
        System.out.println("Output directory: " + outputPath);
        crawlerEngine.run(spiderName);
        System.out.println("Spider dev run finished: " + spiderName);
    }

    private static Map<String, AbstractSpider> createSpiderRegistry() {
        Map<String, AbstractSpider> spiders = new LinkedHashMap<>();
        register(spiders, new TestSpider());
        register(spiders, new WeiXinBQBSpider());
        register(spiders, new YujnApiSpider());
        return spiders;
    }

    private static void register(Map<String, AbstractSpider> spiders, AbstractSpider spider) {
        spiders.put(spider.name(), spider);
    }

    private static String resolveSpiderName(String[] args) {
        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return args[0].trim();
        }
        return DEFAULT_SPIDER_NAME;
    }

    private static String resolveOutputPath(String[] args) {
        if (args.length > 1 && args[1] != null && !args[1].isBlank()) {
            return Path.of(args[1].trim()).toAbsolutePath().normalize().toString();
        }
        return resolveRepositoryRoot().resolve(DEFAULT_OUTPUT_DIR_NAME).toString();
    }

    private static Path resolveRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("one-modules"))
                && Files.isDirectory(current.resolve("one-common"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}

