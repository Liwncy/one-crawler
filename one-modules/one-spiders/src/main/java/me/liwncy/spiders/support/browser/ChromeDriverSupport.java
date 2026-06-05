package me.liwncy.spiders.support.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * ChromeDriver 启动辅助。
 */
public final class ChromeDriverSupport {

    private static final Logger log = LoggerFactory.getLogger(ChromeDriverSupport.class);

    private static final List<String> STALE_LOCK_FILE_NAMES = List.of(
        "SingletonLock",
        "SingletonCookie",
        "SingletonSocket",
        "lockfile",
        "DevToolsActivePort"
    );

    private ChromeDriverSupport() {
    }

    public static WebDriver createChromeDriver(String userDataDir) {
        Path normalizedUserDataDir = normalizeUserDataDir(userDataDir);
        ChromeOptions options = buildChromeOptions(normalizedUserDataDir);
        try {
            return new ChromeDriver(options);
        } catch (SessionNotCreatedException e) {
            if (normalizedUserDataDir == null || !isDevToolsActivePortError(e)) {
                throw e;
            }
            cleanupStaleLockFiles(normalizedUserDataDir);
            try {
                return new ChromeDriver(options);
            } catch (SessionNotCreatedException retryException) {
                return createWithTemporaryProfile(normalizedUserDataDir, retryException);
            }
        }
    }

    private static WebDriver createWithTemporaryProfile(Path normalizedUserDataDir, SessionNotCreatedException retryException) {
        Path temporaryProfileDir = createTemporaryProfileDir();
        log.warn(
            "Chrome user-data-dir unavailable, fallback to temporary profile. configuredDir={}, temporaryDir={}",
            normalizedUserDataDir,
            temporaryProfileDir,
            retryException
        );
        ChromeOptions fallbackOptions = buildChromeOptions(temporaryProfileDir);
        try {
            return new ChromeDriver(fallbackOptions);
        } catch (SessionNotCreatedException fallbackException) {
            fallbackException.addSuppressed(retryException);
            throw new IllegalStateException(
                "Chrome 浏览器启动失败，浏览器资料目录可能被占用或已损坏："
                    + normalizedUserDataDir
                    + "。已尝试自动切换到临时浏览器目录，但仍启动失败。请先关闭所有 Chrome/ChromeDriver 进程，或改用新的 --browser-user-data-dir。",
                fallbackException
            );
        }
    }

    private static ChromeOptions buildChromeOptions(Path userDataDir) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        if (userDataDir != null) {
            cleanupStaleLockFiles(userDataDir);
            options.addArguments("--user-data-dir=" + userDataDir);
        }
        return options;
    }

    private static Path normalizeUserDataDir(String userDataDir) {
        if (userDataDir == null || userDataDir.isBlank()) {
            return null;
        }
        Path normalized = Path.of(userDataDir.trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized);
        } catch (IOException e) {
            throw new IllegalStateException("create chrome user data dir failed: " + normalized, e);
        }
        return normalized;
    }

    private static Path createTemporaryProfileDir() {
        try {
            Path path = Files.createTempDirectory("one-crawler-chrome-");
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("create temporary chrome profile dir failed", e);
        }
    }

    private static void cleanupStaleLockFiles(Path userDataDir) {
        for (String fileName : STALE_LOCK_FILE_NAMES) {
            try {
                Files.deleteIfExists(userDataDir.resolve(fileName));
            } catch (IOException ignored) {
                // 忽略无法清理的锁文件，后续由 ChromeDriver 启动结果决定是否重试。
            }
        }
    }

    private static boolean isDevToolsActivePortError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("DevToolsActivePort file doesn't exist")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

