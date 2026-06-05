package me.liwncy.adapter.webmagic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.liwncy.common.crawler.core.AbstractSpider;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.FrameworkAdapter;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.core.SpiderFramework;
import me.liwncy.common.crawler.core.SpiderParseResult;
import me.liwncy.common.crawler.pipeline.SpiderPipeline;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.processor.PageProcessor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebMagic 框架适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebMagicAdapter implements FrameworkAdapter {

    private final Site webMagicSite;
    private final List<SpiderPipeline> spiderPipelines;

    @Override
    public boolean supports(SpiderFramework framework) {
        return SpiderFramework.WEBMAGIC == framework;
    }

    @Override
    public void run(AbstractSpider spider) {
        SpiderConfig config = spider.getConfig();
        if (config.getStartUrls() == null || config.getStartUrls().isEmpty()) {
            throw new IllegalArgumentException("Spider startUrls can not be empty: " + config.getName());
        }

        spider.beforeStart();
        try {
            applySpiderCookies(spider);
            AtomicReference<RuntimeException> processingFailure = new AtomicReference<>();
            Spider.create(new DelegatingPageProcessor(spider, webMagicSite, processingFailure))
                .addUrl(config.getStartUrls().toArray(String[]::new))
                .addPipeline(new DelegatingPipeline(resolvePipelines(config), config))
                .thread(Math.max(config.getThreadCount(), 1))
                .run();
            if (processingFailure.get() != null) {
                throw processingFailure.get();
            }
        } finally {
            spider.afterFinish();
        }
    }

    private void applySpiderCookies(AbstractSpider spider) {
        if (webMagicSite.getCookies() != null) {
            webMagicSite.getCookies().clear();
        }
        Map<String, String> cookies = spider.getRequestCookies();
        if (cookies != null && !cookies.isEmpty()) {
            cookies.forEach(webMagicSite::addCookie);
        }
    }

    private List<SpiderPipeline> resolvePipelines(SpiderConfig config) {
        if (config.getPipelines() == null || config.getPipelines().isEmpty()) {
            return spiderPipelines.stream()
                .filter(item -> "console".equals(item.getName()))
                .findFirst()
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
        }
        Map<String, SpiderPipeline> pipelineMap = new LinkedHashMap<>();
        spiderPipelines.forEach(item -> pipelineMap.put(item.getName(), item));
        return config.getPipelines().stream()
            .map(name -> {
                SpiderPipeline pipeline = pipelineMap.get(name);
                if (pipeline == null) {
                    throw new IllegalArgumentException("Spider pipeline not found: " + name);
                }
                return pipeline;
            })
            .toList();
    }

    @RequiredArgsConstructor
    private static class DelegatingPageProcessor implements PageProcessor {

        private final AbstractSpider spider;
        private final Site site;
        private final AtomicReference<RuntimeException> processingFailure;

        @Override
        public void process(Page page) {
            SpiderParseResult result;
            try {
                result = spider.parse(page.getRawText(), page.getUrl().toString());
            } catch (RuntimeException e) {
                processingFailure.compareAndSet(null, e);
                throw e;
            }
            if (result == null) {
                page.setSkip(true);
                return;
            }
            if (result.getTargetUrls() != null && !result.getTargetUrls().isEmpty()) {
                page.addTargetRequests(result.getTargetUrls());
            }
            page.putField("items", result.getItems());
            if (result.getItems() == null || result.getItems().isEmpty()) {
                page.setSkip(true);
            }
        }

        @Override
        public Site getSite() {
            return site;
        }
    }

    @RequiredArgsConstructor
    private static class DelegatingPipeline implements us.codecraft.webmagic.pipeline.Pipeline {

        private final List<SpiderPipeline> pipelines;
        private final SpiderConfig config;

        @Override
        public void process(us.codecraft.webmagic.ResultItems resultItems, Task task) {
            List<CrawlResult> items = resultItems.get("items");
            if (items == null || items.isEmpty()) {
                return;
            }
            for (CrawlResult item : items) {
                for (SpiderPipeline pipeline : pipelines) {
                    pipeline.process(item, config);
                }
            }
        }
    }
}

