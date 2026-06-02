package me.liwncy.common.crawler.pipeline;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import me.liwncy.common.crawler.core.CrawlResult;
import me.liwncy.common.crawler.core.SpiderConfig;
import org.springframework.stereotype.Component;

/**
 * 控制台输出管道。
 */
@Slf4j
@Component
public class ConsoleSpiderPipeline implements SpiderPipeline {

    @Override
    public String getName() {
        return "console";
    }

    @Override
    public void process(CrawlResult result, SpiderConfig config) {
        log.info("spider[{}] result => {}", config.getName(), JSONUtil.toJsonStr(result.getFields()));
    }
}

