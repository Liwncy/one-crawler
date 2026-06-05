package me.liwncy.spiders.support.login;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginConfig {

    private String spiderName;
    private String baseUrl;
    private String loginUrl;
    private String entryUrl;
    private LoginCredentials credentials;
    private String usernameSelector;
    private String passwordSelector;
    private String submitSelector;

    /**
     * Cookie 有效期，单位分钟。
     */
    @Builder.Default
    private long sessionTtlMinutes = 120L;

    /**
     * 是否启用人工验证码输入。
     */
    @Builder.Default
    private boolean manualCaptcha = true;

    /**
     * 手工登录自动检测超时，单位毫秒。
     */
    @Builder.Default
    private long manualLoginTimeoutMillis = 300000L;

    /**
     * 自动提交后额外等待时长，单位毫秒。
     */
    @Builder.Default
    private long waitAfterSubmitMillis = 1500L;

    /**
     * 可选：浏览器用户数据目录。
     */
    private String browserUserDataDir;
}

