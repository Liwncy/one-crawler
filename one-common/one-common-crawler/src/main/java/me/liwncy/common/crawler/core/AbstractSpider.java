package me.liwncy.common.crawler.core;

import java.util.Collections;
import java.util.Map;

/**
 * 爬虫业务抽象基类。
 */
public abstract class AbstractSpider {

    /**
     * 爬虫配置
     */
    public abstract SpiderConfig getConfig();

    /**
     * 解析页面内容
     *
     * @param html 页面 html
     * @param url 当前 url
     * @return 解析结果（数据项 + 继续抓取的 url）
     */
    public abstract SpiderParseResult parse(String html, String url);

    /**
     * 启动前回调
     */
    public void beforeStart() {
    }

    /**
     * 结束后回调
     */
    public void afterFinish() {
    }

    /**
     * 当前运行时请求 Cookie。
     */
    public Map<String, String> getRequestCookies() {
        return Collections.emptyMap();
    }

    public String name() {
        return getConfig().getName();
    }
}


