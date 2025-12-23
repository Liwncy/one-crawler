package me.liwncy.webmagic.task;

import cn.hutool.core.date.DateTime;
import lombok.SneakyThrows;
import me.liwncy.common.webmagic.config.WebMagicConfig;
import me.liwncy.common.webmagic.pipeline.OneJsonFilePipeline;
import me.liwncy.webmagic.task.pipeline.WeiXinBQBPipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.scheduler.BloomFilterDuplicateRemover;
import us.codecraft.webmagic.scheduler.QueueScheduler;
import us.codecraft.webmagic.selector.Html;

import java.util.List;

/**
 * @author liwncy
 * 微信表情包
 * 公众号: 月亮不营业
 * <p>
 * 超可爱的蓝色猫耳少女猫羽雫表情包: https://mp.weixin.qq.com/s/e18msKS6mR4DUsOVOJ3z0A
 * 软糯Q弹的飞天泡芙达小白团子表情包: https://mp.weixin.qq.com/s/5d3U9vTR2iJWHhUMefS-sA
 */
@Component
public class WeiXinBQBJobProcessor implements PageProcessor {

    @Autowired
    private WeiXinBQBPipeline weiXinBQBPipeline;

    static String[] BQB_URL_ARR = new String[]{
        // "https://mp.weixin.qq.com/s/e18msKS6mR4DUsOVOJ3z0A",
        // "https://mp.weixin.qq.com/s/z0rb5nN02o0rvQcVWK3WEQ",
        // "https://mp.weixin.qq.com/s/DJ7JY-A0Bu7wXc4PRVlSjA",
        // "https://mp.weixin.qq.com/s/5d3U9vTR2iJWHhUMefS-sA",
        // "https://mp.weixin.qq.com/s/e18msKS6mR4DUsOVOJ3z0A",
        // // 你神经病啊
        // "https://mp.weixin.qq.com/s/uX8T18WXTWZdC55LnOZ9hQ",
        // // 可爱小兔子表情包
        // "https://mp.weixin.qq.com/s/3fAlyNRW7XKXD1AZYijA-Q",
        // // 三丽鸥&像素风高糊动态可爱表情包
        // "https://mp.weixin.qq.com/s/XyHKpzMjmSfn4GJaPIUj4A",
        // // 蛋仔派对表情
        // "https://mp.weixin.qq.com/s/S5K5XWTy5rpcHk3HHlT12w",
        // // sorry！我是个直女！
        // "https://mp.weixin.qq.com/s/EnwbYk0sRsweEnU3j0_y5Q",
        //
        "https://mp.weixin.qq.com/s/GGCB5v-YrhV9xkAMeXzTuQ",
    };

    @Override
    public void process(Page page) {
        // 解析页面
        Html html = page.getHtml();
        String url = page.getUrl().toString();
        System.out.println(html.xpath("//img[@class='rich_pages wxw-img']"));
        List<String> downList = html.xpath("//img[@class='rich_pages wxw-img']/@data-src").all();
        page.putField("title", html.xpath("//h1[@id='activity-name']/text()").toString());
        page.putField("url", url);
        page.putField("picList", downList);
    }

    @Override
    public Site getSite() {
        return WebMagicConfig.getSite();
    }

    /**
     * 爬虫的启动方法
     */
    public void start(PageProcessor pageProcessor) {
        // 开启线程
        Spider.create(pageProcessor)
            // 添加初始url
            .addUrl("https://mp.weixin.qq.com/s/e18msKS6mR4DUsOVOJ3z0A")
            .setScheduler(new QueueScheduler().setDuplicateRemover(new BloomFilterDuplicateRemover(100000)))
            .addPipeline(this.weiXinBQBPipeline)
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
        WeiXinBQBPipeline weiXinBQBPipeline = new WeiXinBQBPipeline();
        Spider.create(new WeiXinBQBJobProcessor())
            .addUrl(BQB_URL_ARR)
            .addPipeline(weiXinBQBPipeline)
            // .addPipeline(new OneJsonFilePipeline("D:\\weixin\\biaoqingbao_" + DateTime.now().toString("yyyyMMddHHmmss") + ".txt"))
            .thread(5)
            .run();
    }
}
