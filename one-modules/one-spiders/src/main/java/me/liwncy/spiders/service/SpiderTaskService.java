package me.liwncy.spiders.service;

import lombok.RequiredArgsConstructor;
import me.liwncy.common.crawler.core.CrawlerEngine;
import me.liwncy.spiders.enums.SpiderTaskStatus;
import me.liwncy.spiders.domain.vo.SpiderTaskInfo;
import me.liwncy.spiders.domain.vo.SpiderTaskRuntimeState;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爬虫任务服务。
 */
@Service
@RequiredArgsConstructor
public class SpiderTaskService {

    private final CrawlerEngine crawlerEngine;
    private final Map<String, SpiderTaskRuntimeState> runtimeStateMap = new ConcurrentHashMap<>();

    public List<SpiderTaskInfo> listSpiders() {
        return crawlerEngine.listSpiderConfigs().stream()
            .map(config -> SpiderTaskInfo.from(config, getRuntimeState(config.getName())))
            .toList();
    }

    public SpiderTaskInfo getSpider(String spiderName) {
        return SpiderTaskInfo.from(crawlerEngine.getSpiderConfig(spiderName), getRuntimeState(spiderName));
    }

    public void run(String spiderName) {
        LocalDateTime startTime = LocalDateTime.now();
        runtimeStateMap.put(spiderName, SpiderTaskRuntimeState.builder()
            .status(SpiderTaskStatus.RUNNING)
            .lastStartTime(startTime)
            .build());
        try {
            crawlerEngine.run(spiderName);
            LocalDateTime endTime = LocalDateTime.now();
            runtimeStateMap.put(spiderName, SpiderTaskRuntimeState.builder()
                .status(SpiderTaskStatus.SUCCESS)
                .lastStartTime(startTime)
                .lastEndTime(endTime)
                .lastDurationMillis(Duration.between(startTime, endTime).toMillis())
                .build());
        } catch (RuntimeException e) {
            LocalDateTime endTime = LocalDateTime.now();
            runtimeStateMap.put(spiderName, SpiderTaskRuntimeState.builder()
                .status(SpiderTaskStatus.FAILED)
                .lastStartTime(startTime)
                .lastEndTime(endTime)
                .lastDurationMillis(Duration.between(startTime, endTime).toMillis())
                .lastErrorMessage(e.getMessage())
                .build());
            throw e;
        }
    }

    private SpiderTaskRuntimeState getRuntimeState(String spiderName) {
        return runtimeStateMap.getOrDefault(spiderName, SpiderTaskRuntimeState.builder().build());
    }
}

