package me.liwncy.common.crawler.core;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 爬虫统一调度入口。
 */
@Component
public class CrawlerEngine {

    private final List<FrameworkAdapter> adapters;
    private final Map<String, AbstractSpider> spiderRegistry;

    public CrawlerEngine(List<FrameworkAdapter> adapters, List<AbstractSpider> spiders) {
        this.adapters = adapters;
        this.spiderRegistry = spiders == null ? Collections.emptyMap() : spiders.stream()
            .collect(Collectors.toMap(AbstractSpider::name, spider -> spider, (left, right) -> left, LinkedHashMap::new));
    }

    public List<String> listSpiders() {
        return spiderRegistry.keySet().stream().sorted().toList();
    }

    public List<SpiderConfig> listSpiderConfigs() {
        return spiderRegistry.values().stream()
            .map(AbstractSpider::getConfig)
            .sorted((left, right) -> left.getName().compareTo(right.getName()))
            .toList();
    }

    public SpiderConfig getSpiderConfig(String spiderName) {
        AbstractSpider spider = spiderRegistry.get(spiderName);
        if (spider == null) {
            throw new IllegalArgumentException("Spider not found: " + spiderName);
        }
        return spider.getConfig();
    }

    public void run(String spiderName) {
        AbstractSpider spider = getSpider(spiderName);
        run(spider);
    }

    public void run(AbstractSpider spider) {
        SpiderFramework framework = spider.getConfig().getFramework();
        FrameworkAdapter adapter = adapters.stream()
            .filter(item -> item.supports(framework))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No adapter found for framework: " + framework));
        adapter.run(spider);
    }

    private AbstractSpider getSpider(String spiderName) {
        AbstractSpider spider = spiderRegistry.get(spiderName);
        if (spider == null) {
            throw new IllegalArgumentException("Spider not found: " + spiderName);
        }
        return spider;
    }
}

