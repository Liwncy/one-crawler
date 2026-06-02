package me.liwncy.adapter.webmagic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import us.codecraft.webmagic.Site;

/**
 * WebMagic 基础配置
 */
@Configuration
public class WebMagicConfig {

    @Bean
    public Site webMagicSite(WebMagicProperties properties) {
        return Site.me()
            .setCycleRetryTimes(properties.getRetryTimes())
            .setRetryTimes(properties.getRetryTimes())
            .setSleepTime(Math.toIntExact(properties.getSleepTime()))
            .setTimeOut(properties.getTimeout())
            .setUserAgent(properties.getUserAgent())
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
            .setCharset(properties.getCharset());
    }
}

