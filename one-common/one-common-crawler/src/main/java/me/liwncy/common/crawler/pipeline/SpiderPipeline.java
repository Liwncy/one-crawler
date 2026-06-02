package me.liwncy.common.crawler.pipeline;

import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;

/**
 * 爬虫结果处理管道。
 */
public interface SpiderPipeline {

    /**
     * 管道名称，用于在 SpiderConfig 中引用。
     */
    String getName();

    /**
     * 处理单条抓取结果。
     */
    void process(CrawlResult result, SpiderConfig config);
}

