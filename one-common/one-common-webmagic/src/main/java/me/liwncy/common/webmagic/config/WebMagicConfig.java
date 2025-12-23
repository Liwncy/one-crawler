package me.liwncy.common.webmagic.config;

import lombok.extern.slf4j.Slf4j;
import me.liwncy.common.core.factory.YmlPropertySourceFactory;
import me.liwncy.common.webmagic.config.properties.WebMagicProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.downloader.HttpClientDownloader;
import us.codecraft.webmagic.utils.ProxyUtils;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(WebMagicProperties.class)
@PropertySource(value = "classpath:common-webmagic.yml", factory = YmlPropertySourceFactory.class)
public class WebMagicConfig {

    @Autowired
    private WebMagicProperties webMagicProperties;

    @Bean
    public static Site getSite() {
        Site site = Site.me()
            .setCycleRetryTimes(5)
            .setRetryTimes(5)
            .setSleepTime(500)
            .setTimeOut(3 * 60 * 1000)
            .setUserAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:38.0) Gecko/20100101 Firefox/38.0")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
            .setCharset("UTF-8");
        return site;
    }

}
