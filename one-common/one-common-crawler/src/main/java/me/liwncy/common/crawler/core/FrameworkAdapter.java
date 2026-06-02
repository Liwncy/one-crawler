package me.liwncy.common.crawler.core;

/**
 * 爬虫框架适配器接口。
 */
public interface FrameworkAdapter {

    /**
     * 当前适配器是否支持指定框架。
     */
    boolean supports(SpiderFramework framework);

    /**
     * 执行爬虫。
     */
    void run(AbstractSpider spider);
}

