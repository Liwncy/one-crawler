package me.liwncy.webmagic.task;

import me.liwncy.webmagic.task.pipeline.TestPipeline;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.scheduler.BloomFilterDuplicateRemover;
import us.codecraft.webmagic.scheduler.QueueScheduler;
import us.codecraft.webmagic.selector.Html;

import java.util.ArrayList;
import java.util.List;

/**
 * @author liwncy
 */
@Component
public class TestJobProcessor implements PageProcessor {

    @Autowired
    private TestPipeline testPipeline;

    private Site site = Site.me()
        // 设置编码格式
        .setCharset("utf8")
        // 设置超时时间
        .setTimeOut(10 * 1000)
        // 设置重试的间隔时间
        .setRetrySleepTime(3 * 1000)
        // 设置重试的次数
        .setRetryTimes(3);

    @Override
    public void process(Page page) {
        if (true) {
            // 解析页面
            Html html = page.getHtml();
            String url = page.getUrl().toString();
            if (url.startsWith("https:")) {
                // https: 会跳转到http:百度页面
                page.addTargetRequest(html.xpath("meta/@content").get().split("=")[1]);
            } else {
                // 接收Map对象
                page.putField("title", html.xpath("//div[@id='s-top-left']"));
                if (page.getResultItems().get("name") == null) {
                    // skip this page
                    page.setSkip(true);
                }
            }
        } else {
            List<String> urls = new ArrayList<>();
            System.setProperty("webdriver.chrome.driver", "src/main/resources/static/chromedriver.exe");
            WebDriver driver = new ChromeDriver();
            String url = page.getUrl().toString();
            driver.get(url);

            driver.close();
            driver.quit();
            // 将待爬取url放入队列中
            page.addTargetRequests(urls);
        }
    }

    @Override
    public Site getSite() {
        return site;
    }

    /**
     * 爬虫的启动方法
     */
    public void start(PageProcessor pageProcessor) {
        // 开启线程
        Spider.create(pageProcessor)
            // 添加初始url
            .addUrl("https://www.baidu.com")
            .setScheduler(new QueueScheduler().setDuplicateRemover(new BloomFilterDuplicateRemover(100000)))
            .addPipeline(this.testPipeline)
            .thread(10)
            .run();
        try {
            System.out.println("爬虫结束");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Spider.create(new TestJobProcessor())
            .addUrl("https://www.baidu.com")
            .thread(5)
            .run();
    }
}
