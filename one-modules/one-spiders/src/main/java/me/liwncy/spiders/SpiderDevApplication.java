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
import me.liwncy.spiders.support.SpiderPaths;
import me.liwncy.spiders.support.login.AbstractLoginSpider;
import me.liwncy.spiders.support.login.FileLoginSessionStore;
import me.liwncy.spiders.support.login.LoginConsoleSupport;
import me.liwncy.spiders.support.login.LoginSupport;
import me.liwncy.spiders.support.login.LoginSessionManager;
import me.liwncy.spiders.support.login.LoginSpider;
import me.liwncy.spiders.support.login.SeleniumManualLoginSupport;
import me.liwncy.spiders.task.LoginDemoSpider;
import me.liwncy.spiders.task.TestSpider;
import me.liwncy.spiders.task.WeiXinBQBSpider;
import me.liwncy.spiders.task.WenshuCourtSpider;
import me.liwncy.spiders.task.YujnApiSpider;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final String DEFAULT_SPIDER_NAME = WenshuCourtSpider.NAME;

    /**
     * 默认输出目录名称。
     */
    private static final String DEFAULT_OUTPUT_DIR_NAME = "data/wenshu";

    public static void main(String[] args) {
        Map<String, AbstractSpider> spiderRegistry = createSpiderRegistry();
        SpiderDevOptions options = SpiderDevOptions.parse(args);
        if (options.listOnly()) {
            printAvailableSpiders(spiderRegistry);
            return;
        }

        String spiderName = options.resolveSpiderName();
        String outputPath = options.resolveOutputPath();

        if (!spiderRegistry.containsKey(spiderName)) {
            System.err.println("Unknown spider: " + spiderName);
            System.err.println("Available spiders: " + String.join(", ", spiderRegistry.keySet()));
            throw new IllegalArgumentException("Unknown spider: " + spiderName);
        }

        configureLoginSpiders(spiderRegistry.values(), options);

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

        clearDevOutputFile(outputPath, spiderName);
        System.out.println("Spider dev run start: " + spiderName);
        System.out.println("Output directory: " + outputPath);
        crawlerEngine.run(spiderName);
        System.out.println("Spider dev run finished: " + spiderName);
    }

    private static void clearDevOutputFile(String outputPath, String spiderName) {
        try {
            Path filePath = Path.of(outputPath).resolve(spiderName + ".jsonl");
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("clear spider dev output failed: " + spiderName, e);
        }
    }

    private static void configureLoginSpiders(Collection<AbstractSpider> spiders, SpiderDevOptions options) {
        LoginSupport loginSupport = new SeleniumManualLoginSupport(new LoginConsoleSupport());
        LoginSessionManager loginSessionManager = new LoginSessionManager(
            new FileLoginSessionStore(),
            loginSupport,
            options.refreshLogin(),
            options.browserUserDataDir()
        );
        for (AbstractSpider spider : spiders) {
            if (spider instanceof AbstractLoginSpider loginSpider) {
                loginSpider.setLoginSessionManager(loginSessionManager);
            }
        }
    }

    private static void printAvailableSpiders(Map<String, AbstractSpider> spiderRegistry) {
        System.out.println("Available spiders:");
        spiderRegistry.values().forEach(spider -> {
            String loginTag = spider instanceof LoginSpider ? " [login]" : "";
            System.out.println("- " + spider.name() + loginTag + " : " + spider.getConfig().getDescription());
        });
    }

    private static Map<String, AbstractSpider> createSpiderRegistry() {
        Map<String, AbstractSpider> spiders = new LinkedHashMap<>();
        register(spiders, new TestSpider());
        register(spiders, new LoginDemoSpider());
        register(spiders, new WenshuCourtSpider());
        register(spiders, new WeiXinBQBSpider());
        register(spiders, new YujnApiSpider());
        return spiders;
    }

    private static void register(Map<String, AbstractSpider> spiders, AbstractSpider spider) {
        spiders.put(spider.name(), spider);
    }

    private record SpiderDevOptions(
        String spiderName,
        String outputPath,
        boolean listOnly,
        boolean refreshLogin,
        String browserUserDataDir
    ) {

        private static SpiderDevOptions parse(String[] args) {
            String spiderName = "";
            String outputPath = "";
            boolean listOnly = false;
            boolean refreshLogin = false;
            String browserUserDataDir = "";
            List<String> positionalArgs = new ArrayList<>();

            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                switch (arg) {
                    case "--list" -> listOnly = true;
                    case "--spider" -> {
                        index++;
                        spiderName = requireValue(args, index, arg);
                    }
                    case "--output" -> {
                        index++;
                        outputPath = normalizePath(requireValue(args, index, arg));
                    }
                    case "--refresh-login" -> refreshLogin = true;
                    case "--browser-user-data-dir" -> {
                        index++;
                        browserUserDataDir = normalizePath(requireValue(args, index, arg));
                    }
                    default -> {
                        if (arg.startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        positionalArgs.add(arg.trim());
                    }
                }
            }

            if (spiderName.isBlank() && !positionalArgs.isEmpty()) {
                spiderName = positionalArgs.get(0);
            }
            if (outputPath.isBlank() && positionalArgs.size() > 1) {
                outputPath = normalizePath(positionalArgs.get(1));
            }

            return new SpiderDevOptions(spiderName, outputPath, listOnly, refreshLogin, browserUserDataDir);
        }

        private static String requireValue(String[] args, int index, String optionName) {
            if (index >= args.length || args[index] == null || args[index].isBlank()) {
                throw new IllegalArgumentException("Missing value for option: " + optionName);
            }
            return args[index].trim();
        }

        private static String normalizePath(String path) {
            return Path.of(path.trim()).toAbsolutePath().normalize().toString();
        }

        private String resolveSpiderName() {
            return spiderName == null || spiderName.isBlank() ? DEFAULT_SPIDER_NAME : spiderName.trim();
        }

        private String resolveOutputPath() {
            if (outputPath != null && !outputPath.isBlank()) {
                return outputPath;
            }
            return SpiderPaths.resolveRepositoryRoot().resolve(DEFAULT_OUTPUT_DIR_NAME).toString();
        }
    }
}

