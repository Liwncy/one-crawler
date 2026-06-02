package me.liwncy.common.crawler.core;

import java.util.Arrays;

/**
 * 支持的爬虫框架类型
 */
public enum SpiderFramework {

    WEBMAGIC("spiders");

    private final String value;

    SpiderFramework(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SpiderFramework fromValue(String value) {
        return Arrays.stream(values())
            .filter(item -> item.value.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported spider framework: " + value));
    }
}

