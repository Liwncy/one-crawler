package me.liwncy.spiders.pipeline;

import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import me.liwncy.common.crawler.config.CrawlerProperties;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.pipeline.SpiderPipeline;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * 将爬虫结果导出为 Swagger 2.0 格式的完整 JSON 文件。
 * <p>
 * 采用内存缓存模式：每条记录调用 process() 时收集数据到内存，
 * Spider 结束后在 afterFinish() 中一次性写入完整的 swagger.json 文件。
 * 可直接导入 Apifox / Swagger Editor 等工具。
 */
@Slf4j
@Component
public class SwaggerJsonlFilePipeline implements SpiderPipeline {

    private final CrawlerProperties crawlerProperties;

    /** 缓存当前爬虫的所有 API 数据 */
    private final List<ApiData> apiDataCache = new ArrayList<>();

    public SwaggerJsonlFilePipeline(CrawlerProperties crawlerProperties) {
        this.crawlerProperties = crawlerProperties;
    }

    @Override
    public String getName() {
        return "swagger-jsonl";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(CrawlResult result, SpiderConfig config) {
        Map<String, Object> htmlInfo = (Map<String, Object>) result.get("html_info");
        if (htmlInfo == null) {
            return;
        }

        ApiData data = new ApiData();
        data.apiName = blankToDefault((String) result.get("keyword"), "Untitled");
        data.description = blankToDefault((String) htmlInfo.get("description"), "");
        data.requestMethod = blankToDefault((String) htmlInfo.get("request_method"), "GET").toUpperCase();
        data.requestAddress = blankToDefault((String) htmlInfo.get("request_address"), "");
        data.requestParamsText = blankToDefault((String) htmlInfo.get("request_params"), "");
        data.returnExample = blankToDefault((String) htmlInfo.get("return_example"), "{}");
        this.apiDataCache.add(data);
    }

    private static String blankToDefault(String str, String defaultValue) {
        return StrUtil.blankToDefault(str, defaultValue);
    }

    /**
     * Spider 结束后写入最终的 Swagger JSON 文件。
     * 子类可重写此方法做额外处理。
     */
    public void afterFinish(SpiderConfig config) {
        if (this.apiDataCache.isEmpty()) {
            log.info("no api data collected, skip swagger export, spider={}", config.getName());
            return;
        }

        try {
            Path basePath = Path.of(crawlerProperties.getOutputPath());
            Files.createDirectories(basePath);
            Path filePath = basePath.resolve(config.getName() + "-swagger.json");

            Map<String, Object> swagger = buildSwaggerDocument(config.getName());
            String json = JSONUtil.toJsonPrettyStr(swagger);
            Files.writeString(filePath, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("swagger json written: {}, {} apis", filePath, this.apiDataCache.size());
        } catch (IOException e) {
            log.error("write swagger json file failed, spider={}", config.getName(), e);
            throw new IllegalStateException("write swagger json file failed", e);
        } finally {
            this.apiDataCache.clear();
        }
    }

    private Map<String, Object> buildSwaggerDocument(String configName) {
        Map<String, Object> swagger = new LinkedHashMap<>();
        swagger.put("swagger", "2.0");

        // info
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", configName);
        info.put("description", "Auto-generated from " + configName);
        info.put("version", "1.0.0");
        swagger.put("info", info);

        // paths
        Map<String, Object> paths = new LinkedHashMap<>();
        for (ApiData data : this.apiDataCache) {
            String pathKey = extractPath(data.requestAddress);
            @SuppressWarnings("unchecked")
            Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(pathKey, k -> new LinkedHashMap<>());

            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("summary", data.apiName);
            if (StrUtil.isNotBlank(data.description)) {
                operation.put("description", data.description);
            }

            List<Map<String, Object>> parameters = parseParameters(data.requestParamsText);
            if (!parameters.isEmpty()) {
                operation.put("parameters", parameters);
            }

            Map<String, Object> response = buildResponse(data.returnExample);
            Map<String, Object> responses = new LinkedHashMap<>();
            responses.put("200", response);
            operation.put("responses", responses);

            pathItem.put(data.requestMethod.toLowerCase(), operation);
        }
        swagger.put("paths", paths);

        return swagger;
    }

    /**
     * 从完整 URL 中提取路径部分。
     * 例如 "https://api.pearapi.ai/api/dy/check.php" -> "/api/dy/check.php"
     */
    private String extractPath(String fullUrl) {
        if (StrUtil.isBlank(fullUrl)) {
            return "/unknown";
        }
        String path;
        int schemaIdx = fullUrl.indexOf("://");
        if (schemaIdx >= 0) {
            path = fullUrl.substring(schemaIdx + 3);
        } else {
            path = fullUrl;
        }
        int slashIdx = path.indexOf('/');
        if (slashIdx >= 0) {
            return path.substring(slashIdx);
        }
        return "/" + path;
    }

    /**
     * 将 request_params 纯文本解析为 Swagger parameters 数组。
     * PearAPI 格式: "fieldName (可选/必填): field description"
     * 或 YAML 格式:
     *   - name: field1
     *     type: string
     */
    private List<Map<String, Object>> parseParameters(String text) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return parameters;
        }

        String cleaned = text.trim();

        // 尝试检测 YAML 格式（JSON 数组 pretty print）
        if (cleaned.startsWith("[")) {
            try {
                Object parsed = JSONUtil.parse(cleaned);
                if (parsed instanceof cn.hutool.json.JSONArray arr && !arr.isEmpty()) {
                    for (Object item : arr) {
                        cn.hutool.json.JSONObject obj = JSONUtil.parseObj(item);
                        Map<String, Object> param = new LinkedHashMap<>();
                        param.put("name", blankToDefault(obj.getStr("name"), blankToDefault(obj.getStr("param"), "")));
                        param.put("in", "query");
                        param.put("type", resolveParamType(obj.getStr("type")));
                        param.put("required", Boolean.TRUE.equals(obj.getInt("required", 0)) || Boolean.TRUE.equals(obj.getInt("must", 0)));
                        String desc = blankToDefault(obj.getStr("description"), obj.getStr("details"));
                        if (StrUtil.isNotBlank(desc)) {
                            param.put("description", desc);
                        }
                        parameters.add(param);
                    }
                    return parameters;
                }
            } catch (Exception e) {
                // 不是有效 JSON，走下面普通文本解析
            }
        }

        // 普通文本格式: "name (required/optional): description" 或纯表格
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (StrUtil.isBlank(line) || line.equals("-") || line.equals("|")) {
                continue;
            }

            String name = "", type = "string", desc = "";
            boolean required = false;

            // 尝试 "name (必填/可选): description" 格式
            int parenIdx = line.indexOf('(');
            int colonIdx = line.indexOf(':');

            if (parenIdx > 0 && colonIdx > parenIdx) {
                name = line.substring(0, parenIdx).trim();
                String opt = line.substring(parenIdx + 1, colonIdx).trim();
                required = opt.contains("必填");
                desc = line.substring(colonIdx + 1).trim();
            } else if (colonIdx > 0) {
                // "name: description" 格式
                name = line.substring(0, colonIdx).trim();
                desc = line.substring(colonIdx + 1).trim();
            } else {
                // 表格格式: 按 | 或空白符分列
                String[] cells = line.split("[|\\t]+");
                for (int i = 0; i < cells.length; i++) {
                    String cell = cells[i].trim();
                    if (cell.isEmpty()) continue;
                    if (name.isEmpty()) {
                        name = cell;
                    } else if (type.equals("string") && !cell.toLowerCase().equals("必填") && !cell.toLowerCase().equals("可选")) {
                        type = cell;
                    } else if (cell.toLowerCase().equals("必填")) {
                        required = true;
                    } else {
                        desc = cell;
                    }
                }
            }

            if (StrUtil.isBlank(name)) continue;

            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", name);
            param.put("in", "query");
            param.put("type", type);
            param.put("required", required);
            if (StrUtil.isNotBlank(desc)) {
                param.put("description", desc);
            }
            parameters.add(param);
        }

        return parameters;
    }

    private String resolveParamType(String type) {
        if (StrUtil.isBlank(type)) return "string";
        switch (type.toLowerCase()) {
            case "int":
            case "integer":
            case "long":
                return "integer";
            case "double":
            case "float":
            case "number":
                return "number";
            case "boolean":
                return "boolean";
            case "array":
            case "list":
                return "array";
            case "object":
            case "map":
                return "object";
            default:
                return "string";
        }
    }

    /**
     * 将 return_example 文本解析为 Swagger response 对象。
     */
    private Map<String, Object> buildResponse(String text) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", "成功");

        if (StrUtil.isBlank(text)) {
            response.put("schema", new LinkedHashMap<>());
            return response;
        }

        String cleaned = text.trim();
        try {
            Object json = JSONUtil.parse(cleaned);
            if (json instanceof cn.hutool.json.JSONArray) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "array");
                if (((cn.hutool.json.JSONArray) json).size() > 0) {
                    schema.put("items", toSchema(((cn.hutool.json.JSONArray) json).get(0)));
                }
                response.put("schema", schema);
                return response;
            }
            if (json instanceof cn.hutool.json.JSONObject) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                cn.hutool.json.JSONObject obj = (cn.hutool.json.JSONObject) json;
                for (String key : obj.keySet()) {
                    properties.put(key, toSchema(obj.get(key)));
                }
                schema.put("properties", properties);
                response.put("schema", schema);
                return response;
            }
        } catch (Exception e) {
            // fallback
        }

        response.put("example", cleaned);
        return response;
    }

    private Map<String, Object> toSchema(Object value) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (value == null) {
            schema.put("type", "null");
        } else if (value instanceof String) {
            schema.put("type", "string");
            schema.put("example", value);
        } else if (value instanceof Number) {
            schema.put("type", "number");
            schema.put("example", value);
        } else if (value instanceof Boolean) {
            schema.put("type", "boolean");
            schema.put("example", value);
        } else {
            schema.put("type", "string");
            schema.put("example", String.valueOf(value));
        }
        return schema;
    }

    private static class ApiData {
        String apiName;
        String description;
        String requestMethod;
        String requestAddress;
        String requestParamsText;
        String returnExample;
    }
}
