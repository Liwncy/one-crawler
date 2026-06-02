package me.liwncy.adapter.webmagic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WebMagic 适配器配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "one.crawler.spiders")
public class WebMagicProperties {

    private int retryTimes = 3;

    private long sleepTime = 1000L;

    private int timeout = 10000;

    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    private String charset = "UTF-8";
}

