package me.liwncy.spiders.support.login;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import me.liwncy.spiders.support.browser.ChromeDriverSupport;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Selenium 人工登录支持。
 */
@RequiredArgsConstructor
public class SeleniumManualLoginSupport implements LoginSupport {

    private final LoginConsoleSupport consoleSupport;

    @Override
    public LoginSession login(LoginConfig config) {
        WebDriver webDriver = ChromeDriverSupport.createChromeDriver(config.getBrowserUserDataDir());
        try {
            String entryUrl = StrUtil.isNotBlank(config.getEntryUrl()) ? config.getEntryUrl().trim() : config.getLoginUrl();
            webDriver.get(entryUrl);
            Set<String> initialCookieNames = cookieNames(webDriver.manage().getCookies());
            autoFillCredentialsIfPossible(webDriver, config);
            if (config.isManualCaptcha()) {
                waitForManualLogin(webDriver, config, initialCookieNames,
                    "请在当前浏览器页面完成账号密码输入、图片验证码识别和登录，完成后按回车继续...");
            } else {
                waitForManualLogin(webDriver, config, initialCookieNames,
                    "请在当前浏览器页面完成登录，完成后按回车继续...");
            }
            if (StrUtil.isNotBlank(config.getEntryUrl())
                && !StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(webDriver.getCurrentUrl()), config.getEntryUrl().trim())) {
                webDriver.get(config.getEntryUrl().trim());
            }
            return LoginSession.builder()
                .spiderName(config.getSpiderName())
                .baseUrl(config.getBaseUrl())
                .cookies(toLoginCookies(webDriver.manage().getCookies()))
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(config.getSessionTtlMinutes()))
                .build();
        } finally {
            webDriver.quit();
        }
    }

    private void waitForManualLogin(WebDriver webDriver, LoginConfig config, Set<String> initialCookieNames, String message) {
        System.out.println(message);
        if (config.isManualCaptcha()) {
            System.out.println("当前为人工验证码登录模式，请在浏览器中完成登录后，回到控制台按回车继续。");
            boolean confirmedByConsole = consoleSupport.waitForEnter("确认登录成功后按回车继续...");
            boolean loginPageObserved = waitUntilLoginPageObserved(webDriver, config, 10000L);
            if (!confirmedByConsole) {
                System.out.println("检测到当前运行环境无法接收控制台回车输入，改为自动检测浏览器中的登录完成状态...");
            }
            if (!confirmedByConsole || isLoginPage(webDriver, config)) {
                if (waitUntilLoginCompleted(webDriver, config, initialCookieNames, loginPageObserved)) {
                    return;
                }
                throw new IllegalStateException("未检测到登录完成，请确认浏览器中已真正登录成功后重试。");
            }
            return;
        }
        System.out.println("程序会等待登录完成；如果自动检测不到，再回控制台按回车继续。");
        boolean loginPageObserved = waitUntilLoginPageObserved(webDriver, config, 10000L);
        if (waitUntilLoginCompleted(webDriver, config, initialCookieNames, loginPageObserved)) {
            return;
        }
        consoleSupport.waitForEnter("暂未自动检测到登录完成，请在确认登录成功后按回车继续...");
    }

    private boolean waitUntilLoginPageObserved(WebDriver webDriver, LoginConfig config, long timeoutMillis) {
        if (isLoginPage(webDriver, config)) {
            return true;
        }
        try {
            return new WebDriverWait(webDriver, Duration.ofMillis(Math.max(timeoutMillis, 1000L)))
                .until(driver -> isLoginPage(driver, config));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean waitUntilLoginCompleted(WebDriver webDriver, LoginConfig config, Set<String> initialCookieNames, boolean loginPageObserved) {
        long timeoutMillis = Math.max(config.getManualLoginTimeoutMillis(), 1000L);
        try {
            return new WebDriverWait(webDriver, Duration.ofMillis(timeoutMillis))
                .until(driver -> {
                    boolean cookiesChanged = hasCookieChanged(driver, initialCookieNames);
                    boolean loginPageNow = isLoginPage(driver, config);
                    if (loginPageObserved) {
                        return cookiesChanged && !loginPageNow;
                    }
                    return cookiesChanged && !loginPageNow;
                });
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean hasCookieChanged(WebDriver webDriver, Set<String> initialCookieNames) {
        Set<String> currentCookieNames = cookieNames(webDriver.manage().getCookies());
        return !currentCookieNames.isEmpty() && !currentCookieNames.equals(initialCookieNames);
    }

    private Set<String> cookieNames(Set<Cookie> cookies) {
        Set<String> names = new HashSet<>();
        for (Cookie cookie : cookies) {
            if (cookie != null && cookie.getName() != null) {
                names.add(cookie.getName());
            }
        }
        return names;
    }

    private boolean isLoginPage(WebDriver webDriver, LoginConfig config) {
        String currentUrl = webDriver.getCurrentUrl();
        if (StrUtil.containsIgnoreCase(currentUrl, "open=login")) {
            return true;
        }
        if (StrUtil.isBlank(config.getLoginUrl())) {
            return false;
        }
        return StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(currentUrl), StrUtil.nullToEmpty(config.getLoginUrl()));
    }

    private void autoFillCredentialsIfPossible(WebDriver webDriver, LoginConfig config) {
        LoginCredentials credentials = config.getCredentials();
        if (credentials == null) {
            return;
        }
        boolean filled = fillInput(webDriver, config.getUsernameSelector(), credentials.getUsername());
        filled = fillInput(webDriver, config.getPasswordSelector(), credentials.getPassword()) || filled;
        if (filled) {
            clickIfPresent(webDriver, config.getSubmitSelector());
            sleepQuietly(config.getWaitAfterSubmitMillis());
        }
    }

    private boolean fillInput(WebDriver webDriver, String selector, String value) {
        if (StrUtil.isBlank(selector) || value == null) {
            return false;
        }
        WebElement element = findByCss(webDriver, selector);
        if (element == null) {
            return false;
        }
        element.clear();
        element.sendKeys(value);
        return true;
    }

    private void clickIfPresent(WebDriver webDriver, String selector) {
        if (StrUtil.isBlank(selector)) {
            return;
        }
        WebElement element = findByCss(webDriver, selector);
        if (element != null) {
            element.click();
        }
    }

    private WebElement findByCss(WebDriver webDriver, String selector) {
        try {
            return webDriver.findElement(By.cssSelector(selector.trim()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting login submit", e);
        }
    }

    private List<LoginCookie> toLoginCookies(java.util.Set<Cookie> cookies) {
        return cookies.stream()
            .map(cookie -> LoginCookie.builder()
                .name(cookie.getName())
                .value(cookie.getValue())
                .domain(cookie.getDomain())
                .path(cookie.getPath())
                .expiryEpochSecond(cookie.getExpiry() == null ? null : Instant.ofEpochMilli(cookie.getExpiry().getTime()).getEpochSecond())
                .secure(cookie.isSecure())
                .httpOnly(cookie.isHttpOnly())
                .build())
            .toList();
    }
}


