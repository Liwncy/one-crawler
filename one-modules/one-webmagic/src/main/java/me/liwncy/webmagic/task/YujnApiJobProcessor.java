package me.liwncy.webmagic.task;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.StrUtil;
import lombok.SneakyThrows;
import me.liwncy.common.webmagic.pipeline.OneJsonFilePipeline;
import me.liwncy.webmagic.task.pipeline.YujnApiPipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.scheduler.BloomFilterDuplicateRemover;
import us.codecraft.webmagic.scheduler.QueueScheduler;
import us.codecraft.webmagic.selector.Html;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author liwncy
 */
@Component
public class YujnApiJobProcessor implements PageProcessor {

    @Autowired
    private YujnApiPipeline pipeline;

    private Site site = Site.me().setCycleRetryTimes(5).setRetryTimes(5).setSleepTime(500).setTimeOut(3 * 60 * 1000)
            .setUserAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:38.0) Gecko/20100101 Firefox/38.0")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
            .setCharset("UTF-8");

    @Override
    public void process(Page page) {
        // 解析页面
        Html html = page.getHtml();
        String url = page.getUrl().toString();
        if ("https://api.yujn.cn".equals(url)) {
            List<String> aurls = html.xpath("//div[@class='mdui-container mdui-col']//button/@onclick").all();
            for (String aurl : aurls) {
                if (StrUtil.isNotEmpty(aurl)) {
                    page.addTargetRequest("https://api.yujn.cn/" + aurl.substring(aurl.indexOf("'") + 1, aurl.lastIndexOf("'")));
                }
            }
        } else {
            page.putField("id", url);
            Map<String, Object> htmlInfo = new HashMap<String, Object>() {{
                put("title", html.xpath("//a[@class='mdui-typo-title']/text()").get());
                put("description", html.xpath("//div[@class='mdui-text-color-white-text mdui-valign mdui-color-theme-accent']//font/text()").get());
                put("request_address", html.xpath("//div[@class='mdui-typo']//p[@id='rest_url']//a/@href").all().get(0));
                put("request_example", html.xpath("//div[@class='mdui-typo']//p[@id='rest_url']//a/@href").all().get(1));
                put("request_method", html.xpath("//div[@class='mdui-typo']//p//kbd/text()").get());
                put("request_params", html.xpath("//div[@class='mdui-typo']//table/text()").get());
                put("return_format", html.xpath("//div[@class='mdui-typo']//p//code/text()").get());
                put("return_example", html.xpath("//div[@class='mdui-typo']//pre//code/text()").get());
            }};
            page.putField("html_info", htmlInfo);
            page.putField("keyword", htmlInfo.get("title"));
            page.putField("url", htmlInfo.get("request_address"));
            page.putField("mode", htmlInfo.get("return_format").toString().toLowerCase());
            page.putField("jsonPath", "$");
            page.putField("fileType", htmlInfo.get("return_format").toString().toLowerCase());
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
                .addPipeline(this.pipeline)
                .thread(10)
                .run();
        try {
            System.out.println("爬虫采集已结束,请在数据库中进行查看,或导出为Excel格式进行查看！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SneakyThrows
    public static void main(String[] args) {
        YujnApiPipeline yujnApiPipeline = new YujnApiPipeline();
        Spider.create(new YujnApiJobProcessor())
                .addUrl("https://api.yujn.cn")
                .addPipeline(new OneJsonFilePipeline("D:\\webmagic\\yujn_" + DateTime.now().toString("yyyyMMddHHmmss") + ".txt"))
                .thread(5)
                .run();
    }
}
