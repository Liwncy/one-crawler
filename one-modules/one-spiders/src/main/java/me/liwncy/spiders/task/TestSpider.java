package me.liwncy.spiders.task;

import cn.hutool.core.util.StrUtil;
import me.liwncy.common.crawler.core.AbstractSpider;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.common.crawler.core.SpiderFramework;
import me.liwncy.common.crawler.core.SpiderParseResult;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.selector.Html;

import java.util.List;

/**
 * 新版测试蜘蛛示例。
 */
@Component
public class TestSpider extends AbstractSpider {

    public static final String NAME = "test-spider";

    @Override
    public SpiderConfig getConfig() {
        return SpiderConfig.builder()
            .name(NAME)
            .description("WebMagic 抽象层测试蜘蛛，默认抓取百度首页")
            .framework(SpiderFramework.WEBMAGIC)
            .startUrls(List.of("https://www.baidu.com"))
            .threadCount(1)
            .pipelines(List.of("console", "file"))
            .build();
    }

    @Override
    public SpiderParseResult parse(String htmlText, String url) {
        Html html = new Html(htmlText, url);
        CrawlResult result = CrawlResult.builder().build()
            .field("url", url)
            .field("title", StrUtil.nullToDefault(html.xpath("//title/text()").get(), ""))
            .field("topLeftMenus", html.xpath("//div[@id='s-top-left']//a/text()").all());
        return SpiderParseResult.empty().addItem(result);
    }
}

