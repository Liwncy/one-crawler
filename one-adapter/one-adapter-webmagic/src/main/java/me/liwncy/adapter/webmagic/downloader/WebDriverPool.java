package me.liwncy.adapter.webmagic.downloader;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebDriver 池。
 */
class WebDriverPool {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int DEFAULT_CAPACITY = 5;
    private static final int STAT_RUNNING = 1;
    private static final int STAT_CLOSED = 2;
    private static final String DEFAULT_CONFIG_FILE = "/data/spiders/spiders-selenium/config.ini";
    private static final String DRIVER_FIREFOX = "firefox";
    private static final String DRIVER_CHROME = "chrome";
    private static final String DRIVER_PHANTOMJS = "phantomjs";

    private final int capacity;
    private final AtomicInteger stat = new AtomicInteger(STAT_RUNNING);
    private final List<WebDriver> webDriverList = Collections.synchronizedList(new ArrayList<>());
    private final BlockingDeque<WebDriver> innerQueue = new LinkedBlockingDeque<>();

    private WebDriver driver;

    protected static Properties config;
    protected static DesiredCapabilities caps;

    WebDriverPool(int capacity) {
        this.capacity = capacity;
    }

    WebDriverPool() {
        this(DEFAULT_CAPACITY);
    }

    public WebDriver get() throws InterruptedException {
        checkRunning();
        WebDriver poll = innerQueue.poll();
        if (poll != null) {
            return poll;
        }
        if (webDriverList.size() < capacity) {
            synchronized (webDriverList) {
                if (webDriverList.size() < capacity) {
                    try {
                        configure();
                        innerQueue.add(driver);
                        webDriverList.add(driver);
                    } catch (IOException e) {
                        throw new IllegalStateException("init webdriver failed", e);
                    }
                }
            }
        }
        return innerQueue.take();
    }

    public void returnToPool(WebDriver webDriver) {
        checkRunning();
        innerQueue.add(webDriver);
    }

    public void closeAll() {
        if (!stat.compareAndSet(STAT_RUNNING, STAT_CLOSED)) {
            throw new IllegalStateException("Already closed!");
        }
        for (WebDriver webDriver : webDriverList) {
            logger.info("Quit webDriver {}", webDriver);
            webDriver.quit();
        }
    }

    private void configure() throws IOException {
        config = new Properties();
        String configFile = System.getProperty("selenuim_config", DEFAULT_CONFIG_FILE);
        config.load(new FileReader(configFile));

        caps = new DesiredCapabilities();
        caps.setCapability("takesScreenshot", false);
        String currentDriver = config.getProperty("driver", DRIVER_PHANTOMJS);

        if (DRIVER_PHANTOMJS.equals(currentDriver)) {
            if (config.getProperty("phantomjs_exec_path") != null) {
                caps.setCapability(
                    PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY,
                    config.getProperty("phantomjs_exec_path")
                );
            } else {
                throw new IOException("Property '" + PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY + "' not set!");
            }
            if (config.getProperty("phantomjs_driver_path") != null) {
                caps.setCapability(
                    PhantomJSDriverService.PHANTOMJS_GHOSTDRIVER_PATH_PROPERTY,
                    config.getProperty("phantomjs_driver_path")
                );
            }
        }

        ArrayList<String> cliArgsCap = new ArrayList<>();
        cliArgsCap.add("--web-security=false");
        cliArgsCap.add("--ssl-protocol=any");
        cliArgsCap.add("--ignore-ssl-errors=true");
        caps.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, cliArgsCap);
        caps.setCapability(
            PhantomJSDriverService.PHANTOMJS_GHOSTDRIVER_CLI_ARGS,
            new String[]{"--logLevel=" + config.getProperty("phantomjs_driver_loglevel", "INFO")}
        );

        if (isUrl(currentDriver)) {
            caps.setBrowserName("phantomjs");
            driver = new RemoteWebDriver(new URL(currentDriver), caps);
        } else if (DRIVER_FIREFOX.equals(currentDriver)) {
            driver = new FirefoxDriver(new FirefoxOptions(caps));
        } else if (DRIVER_CHROME.equals(currentDriver)) {
            driver = new ChromeDriver(new ChromeOptions().merge(caps));
        } else if (DRIVER_PHANTOMJS.equals(currentDriver)) {
            driver = new PhantomJSDriver(caps);
        }
    }

    private void checkRunning() {
        if (!stat.compareAndSet(STAT_RUNNING, STAT_RUNNING)) {
            throw new IllegalStateException("Already closed!");
        }
    }

    private boolean isUrl(String urlString) {
        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException ignored) {
            return false;
        }
    }
}


