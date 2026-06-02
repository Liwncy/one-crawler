package me.liwncy.adapter.webmagic.downloader;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.downloader.AbstractDownloader;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.PlainText;
import us.codecraft.webmagic.utils.HttpConstant;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * 使用 Selenium 调用浏览器进行渲染。
 */
public class SeleniumDownloader extends AbstractDownloader implements Closeable {

    private volatile WebDriverPool webDriverPool;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int sleepTime = 0;

    private int poolSize = 1;

    public SeleniumDownloader(String chromeDriverPath) {
        System.getProperties().setProperty("webdriver.chrome.driver", chromeDriverPath);
    }

    public SeleniumDownloader() {
    }

    public SeleniumDownloader setSleepTime(int sleepTime) {
        this.sleepTime = sleepTime;
        return this;
    }

    @Override
    public Page download(Request request, Task task) {
        checkInit();
        WebDriver webDriver = null;
        Page page = Page.ofFailure(request);
        try {
            webDriver = webDriverPool.get();
            logger.info("downloading page {}", request.getUrl());
            webDriver.get(request.getUrl());
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
            WebDriver.Options manage = webDriver.manage();
            Site site = task.getSite();
            if (site.getCookies() != null) {
                for (Map.Entry<String, String> cookieEntry : site.getCookies().entrySet()) {
                    Cookie cookie = new Cookie(cookieEntry.getKey(), cookieEntry.getValue());
                    manage.addCookie(cookie);
                }
            }
            WebElement webElement = webDriver.findElement(By.xpath("/html"));
            String content = webElement.getAttribute("outerHTML");
            page.setDownloadSuccess(true);
            page.setRawText(content);
            page.setHtml(new Html(content, request.getUrl()));
            page.setUrl(new PlainText(request.getUrl()));
            page.setRequest(request);
            page.setStatusCode(HttpConstant.StatusCode.CODE_200);
            onSuccess(page, task);
        } catch (Exception e) {
            logger.warn("download page {} error", request.getUrl(), e);
            onError(page, task, e);
        } finally {
            if (webDriver != null) {
                webDriverPool.returnToPool(webDriver);
            }
        }
        return page;
    }

    private void checkInit() {
        if (webDriverPool == null) {
            synchronized (this) {
                if (webDriverPool == null) {
                    webDriverPool = new WebDriverPool(poolSize);
                }
            }
        }
    }

    @Override
    public void setThread(int thread) {
        this.poolSize = thread;
    }

    @Override
    public void close() throws IOException {
        if (webDriverPool != null) {
            webDriverPool.closeAll();
        }
    }
}

