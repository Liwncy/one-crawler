package me.liwncy.spiders.task;

import cn.hutool.core.util.StrUtil;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.core.SpiderFramework;
import me.liwncy.common.crawler.core.SpiderParseResult;
import me.liwncy.spiders.support.login.AbstractLoginSpider;
import me.liwncy.spiders.support.login.LoginConfig;
import me.liwncy.spiders.support.login.LoginCredentials;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.selector.Html;

import java.util.List;

/**
 * 登录型 Spider 模板。
 * <p>
 * 支持两种使用方式：
 * 1. 直接复制本类并修改常量/解析逻辑；
 * 2. 通过系统属性或环境变量覆盖模板配置，快速验证登录流程。
 * <p>
 * 当前模板额外支持：
 * 1. 自动填充账号密码输入框；
 * 2. 自动点击登录按钮；
 * 3. 进入受保护页后按关键字校验登录是否成功；
 * 4. 如果页面仍然命中登录页关键字，可直接抛错中断。
 */
@Component
public class LoginDemoSpider extends AbstractLoginSpider {

    public static final String NAME = "login-demo";

    private static final String PROPERTY_PREFIX = "spider.login.demo.";

    private static final String LOGIN_URL = "https://example.com/login";
    private static final String PROTECTED_URL = "https://example.com/protected";
    private static final String BROWSER_USER_DATA_DIR = "";
    private static final String USERNAME_SELECTOR = "input[name='username']";
    private static final String PASSWORD_SELECTOR = "input[type='password']";
    private static final String SUBMIT_SELECTOR = "button[type='submit']";

    @Override
    public SpiderConfig getConfig() {
        DemoSettings settings = DemoSettings.load();
        return SpiderConfig.builder()
            .name(NAME)
            .description("登录型 Spider 模板：支持通过系统属性覆盖登录页、目标页和验证关键字")
            .framework(SpiderFramework.WEBMAGIC)
            .startUrls(List.of(settings.protectedUrl()))
            .threadCount(1)
            .pipelines(List.of("console", "file"))
            .build();
    }

    @Override
    public LoginConfig getLoginConfig() {
        DemoSettings settings = DemoSettings.load();
        return LoginConfig.builder()
            .spiderName(NAME)
            .baseUrl(settings.protectedUrl())
            .loginUrl(settings.loginUrl())
            .credentials(LoginCredentials.builder()
                .username(readValue(PROPERTY_PREFIX + "username"))
                .password(readValue(PROPERTY_PREFIX + "password"))
                .build())
            .usernameSelector(settings.usernameSelector())
            .passwordSelector(settings.passwordSelector())
            .submitSelector(settings.submitSelector())
            .sessionTtlMinutes(settings.sessionTtlMinutes())
            .manualCaptcha(settings.manualCaptcha())
            .waitAfterSubmitMillis(settings.waitAfterSubmitMillis())
            .browserUserDataDir(settings.browserUserDataDir())
            .build();
    }

    @Override
    protected void validateAuthenticatedPage(String htmlText, String url) {
        DemoSettings settings = DemoSettings.load();
        if (settings.shouldFailWhenLoginPageDetected(htmlText)) {
            throw new IllegalStateException("login-demo 校验失败：页面仍然匹配登录页关键字，请确认 Cookie 是否生效或登录是否成功。");
        }
        if (settings.shouldValidateProtectedPage(url) && !settings.matches(htmlText, extractTitle(htmlText, url))) {
            throw new IllegalStateException("login-demo 校验失败：受保护页面未匹配 expectedKeyword，请调整关键字或检查登录状态。");
        }
    }

    @Override
    protected SpiderParseResult doParseAuthenticated(String htmlText, String url) {
        DemoSettings settings = DemoSettings.load();
        Html html = new Html(htmlText, url);
        String title = StrUtil.nullToDefault(html.xpath("//title/text()").get(), "");
        CrawlResult result = CrawlResult.builder().build()
            .field("url", url)
            .field("title", title)
            .field("htmlLength", htmlText == null ? 0 : htmlText.length())
            .field("loginCookieNames", getRequestCookies().keySet())
            .field("templateConfigured", settings.isConfigured())
            .field("loginUrl", settings.loginUrl())
            .field("protectedUrl", settings.protectedUrl())
            .field("manualCaptcha", settings.manualCaptcha())
            .field("expectedKeyword", settings.expectedKeyword())
            .field("keywordMatched", settings.matches(htmlText, title))
            .field("loginPageKeyword", settings.loginPageKeyword())
            .field("loginPageDetected", settings.isLoginPage(htmlText))
            .field("usernameSelector", settings.usernameSelector())
            .field("passwordSelector", settings.passwordSelector())
            .field("submitSelector", settings.submitSelector())
            .field("tips", List.of(
                "这是登录型 Spider 模板，请按目标网站调整 XPath / CSS 选择器。",
                "可通过系统属性覆盖：spider.login.demo.login-url、spider.login.demo.protected-url、spider.login.demo.expected-keyword。",
                "如已知登录表单的 CSS 选择器，可配置 username-selector、password-selector、submit-selector 自动填充。",
                "如需复用本机浏览器登录态，可配置 browserUserDataDir 或运行参数 --browser-user-data-dir。",
                "首次运行会弹出浏览器，手工登录后回到控制台按回车继续。"
            ));
        return SpiderParseResult.empty().addItem(result);
    }

    private String extractTitle(String htmlText, String url) {
        Html html = new Html(htmlText, url);
        return StrUtil.nullToDefault(html.xpath("//title/text()").get(), "");
    }

    private long readLong(String key, long defaultValue) {
        String value = readValue(key);
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long value for " + key + ": " + value, e);
        }
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        String value = readValue(key);
        return StrUtil.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private String readValue(String key) {
        String systemValue = System.getProperty(key);
        if (StrUtil.isNotBlank(systemValue)) {
            return systemValue.trim();
        }
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (StrUtil.isNotBlank(envValue)) {
            return envValue.trim();
        }
        return StrUtil.EMPTY;
    }

    private record DemoSettings(
        String loginUrl,
        String protectedUrl,
        String browserUserDataDir,
        String usernameSelector,
        String passwordSelector,
        String submitSelector,
        long sessionTtlMinutes,
        long waitAfterSubmitMillis,
        boolean manualCaptcha,
        String expectedKeyword,
        String loginPageKeyword,
        boolean failWhenLoginPageDetected
    ) {

        private static DemoSettings load() {
            LoginDemoSpider spider = new LoginDemoSpider();
            return new DemoSettings(
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "login-url"), LOGIN_URL),
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "protected-url"), PROTECTED_URL),
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "browser-user-data-dir"), BROWSER_USER_DATA_DIR),
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "username-selector"), USERNAME_SELECTOR),
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "password-selector"), PASSWORD_SELECTOR),
                StrUtil.blankToDefault(spider.readValue(PROPERTY_PREFIX + "submit-selector"), SUBMIT_SELECTOR),
                spider.readLong(PROPERTY_PREFIX + "session-ttl-minutes", 120L),
                spider.readLong(PROPERTY_PREFIX + "wait-after-submit-millis", 1500L),
                spider.readBoolean(PROPERTY_PREFIX + "manual-captcha", true),
                spider.readValue(PROPERTY_PREFIX + "expected-keyword"),
                spider.readValue(PROPERTY_PREFIX + "login-page-keyword"),
                spider.readBoolean(PROPERTY_PREFIX + "fail-when-login-page-detected", true)
            );
        }

        private boolean isConfigured() {
            return !LOGIN_URL.equals(loginUrl) || !PROTECTED_URL.equals(protectedUrl);
        }

        private boolean matches(String htmlText, String title) {
            if (StrUtil.isBlank(expectedKeyword)) {
                return true;
            }
            return StrUtil.containsIgnoreCase(title, expectedKeyword)
                || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(htmlText), expectedKeyword);
        }

        private boolean isLoginPage(String htmlText) {
            if (StrUtil.isBlank(loginPageKeyword)) {
                return false;
            }
            return StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(htmlText), loginPageKeyword);
        }

        private boolean shouldFailWhenLoginPageDetected(String htmlText) {
            return failWhenLoginPageDetected && isLoginPage(htmlText);
        }

        private boolean shouldValidateProtectedPage(String url) {
            return StrUtil.isNotBlank(expectedKeyword)
                && StrUtil.isNotBlank(protectedUrl)
                && StrUtil.startWithIgnoreCase(StrUtil.nullToEmpty(url), protectedUrl);
        }
    }
}

