package me.liwncy.common.crawler.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一抓取结果模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlResult {

    @Builder.Default
    private Map<String, Object> fields = new LinkedHashMap<>();

    public CrawlResult field(String key, Object value) {
        this.fields.put(key, value);
        return this;
    }

    public Object get(String key) {
        return this.fields.get(key);
    }
}

