package me.liwncy.common.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 爬虫通用配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "one.crawler")
public class CrawlerProperties {

    /**
     * 默认结果输出目录
     */
    private String outputPath = "./data";
}

