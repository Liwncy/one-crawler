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
 * 微信表情包新版蜘蛛。
 */
@Component
public class WeiXinBQBSpider extends AbstractSpider {

    public static final String NAME = "weixin-bqb";

    static final String[] BQB_URL_ARR = new String[]{
        "https://mp.weixin.qq.com/s/GGCB5v-YrhV9xkAMeXzTuQ"
    };

    @Override
    public SpiderConfig getConfig() {
        return SpiderConfig.builder()
            .name(NAME)
            .description("采集微信公众号文章中的表情包图片并下载到本地")
            .framework(SpiderFramework.WEBMAGIC)
            .startUrls(List.of(BQB_URL_ARR))
            .threadCount(3)
            .pipelines(List.of("weixin-bqb-download"))
            .build();
    }

    @Override
    public SpiderParseResult parse(String htmlText, String url) {
        Html html = new Html(htmlText, url);
        List<String> picList = html.xpath("//img[@class='rich_pages wxw-img']/@data-src").all();
        if (picList == null || picList.isEmpty()) {
            return SpiderParseResult.empty();
        }
        String title = StrUtil.nullToDefault(html.xpath("//h1[@id='activity-name']/text()").get(), "weixin-bqb");
        CrawlResult crawlResult = CrawlResult.builder().build()
            .field("title", title)
            .field("url", url)
            .field("picList", picList);
        return SpiderParseResult.empty().addItem(crawlResult);
    }
}

