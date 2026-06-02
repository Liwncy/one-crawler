package me.liwncy.common.crawler.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 爬虫定义配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderConfig {

    /**
     * 爬虫名称，作为任务唯一标识
     */
    private String name;

    /**
     * 爬虫描述
     */
    private String description;

    /**
     * 框架类型
     */
    @Builder.Default
    private SpiderFramework framework = SpiderFramework.WEBMAGIC;

    /**
     * 初始 URL 列表
     */
    @Builder.Default
    private List<String> startUrls = new ArrayList<>();

    /**
     * 线程数
     */
    @Builder.Default
    private int threadCount = 1;

    /**
     * 结果管道名称
     */
    @Builder.Default
    private List<String> pipelines = new ArrayList<>();
}

