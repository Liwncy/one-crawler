package me.liwncy.spiders.domain.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 测试GET（内）接口实体
 */
@Data
public class ApiFoxImport {

    /**
     * API名称
     */
    private String name;

    /**
     * API基本信息
     */
    private ApiInfo api;

    /**
     * API基本信息内部类
     */
    @Data
    public static class ApiInfo {

        /**
         * API ID
         */
        private String id;

        /**
         * 请求方法
         */
        private String method;

        /**
         * 请求路径
         */
        private String path;

        /**
         * 请求参数
         */
        private Parameters parameters;

        /**
         * 认证信息
         */
        private Map<String, Object> auth;

        /**
         * 安全方案
         */
        private Map<String, Object> securityScheme;

        /**
         * 通用参数
         */
        private CommonParameters commonParameters;

        /**
         * 响应列表
         */
        private List<Response> responses;

        /**
         * 响应示例
         */
        private List<Object> responseExamples;

        /**
         * 请求体
         */
        private RequestBody requestBody;

        /**
         * 接口描述
         */
        private String description;

        /**
         * 标签
         */
        private List<String> tags;

        /**
         * 状态
         */
        private String status;

        /**
         * 服务器ID
         */
        private String serverId;

        /**
         * 操作ID
         */
        private String operationId;

        /**
         * 源URL
         */
        private String sourceUrl;

        /**
         * 排序
         */
        private Integer ordering;

        /**
         * 测试用例
         */
        private List<Object> cases;

        /**
         * Mock数据
         */
        private List<Object> mocks;

        /**
         * 自定义API字段
         */
        private String customApiFields;

        /**
         * 高级设置
         */
        private AdvancedSettings advancedSettings;

        /**
         * Mock脚本
         */
        private Map<String, Object> mockScript;

        /**
         * 代码示例
         */
        private List<Object> codeSamples;

        /**
         * 通用响应状态
         */
        private Map<String, Object> commonResponseStatus;

        /**
         * 响应子项
         */
        private List<Object> responseChildren;

        /**
         * 可见性
         */
        private String visibility;

        /**
         * 模块ID
         */
        private Long moduleId;

        /**
         * OAS扩展
         */
        private String oasExtensions;

        /**
         * 类型
         */
        private String type;

        /**
         * 前置处理器
         */
        private List<Object> preProcessors;

        /**
         * 后置处理器
         */
        private List<Object> postProcessors;

        /**
         * 继承的后置处理器
         */
        private Map<String, Object> inheritPostProcessors;

        /**
         * 继承的前置处理器
         */
        private Map<String, Object> inheritPreProcessors;
    }

    /**
     * 参数内部类
     */
    @Data
    public static class Parameters {

        /**
         * 查询参数
         */
        private List<QueryParameter> query;
    }

    /**
     * 查询参数内部类
     */
    @Data
    public static class QueryParameter {

        /**
         * 是否必需
         */
        private Boolean required;

        /**
         * 描述
         */
        private String description;

        /**
         * 类型
         */
        private String type;

        /**
         * 参数ID
         */
        private String id;

        /**
         * 是否启用
         */
        private Boolean enable;

        /**
         * 参数名称
         */
        private String name;

        /**
         * 示例
         */
        private String example;
    }

    /**
     * 通用参数内部类
     */
    @Data
    public static class CommonParameters {

        /**
         * 查询参数
         */
        private List<Object> query;

        /**
         * 请求体参数
         */
        private List<Object> body;

        /**
         * Cookie参数
         */
        private List<Object> cookie;

        /**
         * 请求头参数
         */
        private List<Object> header;
    }

    /**
     * 响应内部类
     */
    @Data
    public static class Response {

        /**
         * 响应ID
         */
        private String id;

        /**
         * 响应码
         */
        private Integer code;

        /**
         * 响应名称
         */
        private String name;

        /**
         * JSON Schema
         */
        private JsonSchema jsonSchema;

        /**
         * Item Schema
         */
        private Map<String, Object> itemSchema;

        /**
         * 内容类型
         */
        private String contentType;
    }

    /**
     * JSON Schema内部类
     */
    @Data
    public static class JsonSchema {

        /**
         * 类型
         */
        private String type;

        /**
         * 属性
         */
        private Map<String, Object> properties;
    }

    /**
     * 请求体内部类
     */
    @Data
    public static class RequestBody {

        /**
         * 类型
         */
        private String type;

        /**
         * 参数列表
         */
        private List<Object> parameters;

        /**
         * 是否必需
         */
        private Boolean required;

        /**
         * 媒体类型
         */
        private String mediaType;

        /**
         * 示例
         */
        private List<Object> examples;

        /**
         * OAS扩展
         */
        private String oasExtensions;
    }

    /**
     * 高级设置内部类
     */
    @Data
    public static class AdvancedSettings {

        /**
         * 禁用的系统头
         */
        private Map<String, Object> disabledSystemHeaders;
    }
}
