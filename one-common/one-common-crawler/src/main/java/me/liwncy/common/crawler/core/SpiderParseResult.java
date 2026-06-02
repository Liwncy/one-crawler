package me.liwncy.common.crawler.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 统一解析结果，既包含数据项，也包含待继续抓取的链接。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderParseResult {

    @Builder.Default
    private List<CrawlResult> items = new ArrayList<>();

    @Builder.Default
    private List<String> targetUrls = new ArrayList<>();

    public static SpiderParseResult empty() {
        return SpiderParseResult.builder().build();
    }

    public SpiderParseResult addItem(CrawlResult item) {
        this.items.add(item);
        return this;
    }

    public SpiderParseResult addItems(Collection<CrawlResult> items) {
        this.items.addAll(items);
        return this;
    }

    public SpiderParseResult addTargetUrl(String url) {
        this.targetUrls.add(url);
        return this;
    }

    public SpiderParseResult addTargetUrls(Collection<String> urls) {
        this.targetUrls.addAll(urls);
        return this;
    }
}

