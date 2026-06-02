package me.liwncy.spiders.domain.vo;

import lombok.Builder;
import lombok.Value;
import me.liwncy.common.crawler.core.SpiderConfig;
import me.liwncy.spiders.enums.SpiderTaskStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 蜘蛛任务元信息。
 */
@Value
@Builder
public class SpiderTaskInfo {

    String name;
    String description;
    String framework;
    int threadCount;
    int startUrlCount;
    List<String> startUrls;
    List<String> pipelines;
    SpiderTaskStatus status;
    LocalDateTime lastStartTime;
    LocalDateTime lastEndTime;
    Long lastDurationMillis;
    String lastErrorMessage;

    public static SpiderTaskInfo from(SpiderConfig config, SpiderTaskRuntimeState runtimeState) {
        return SpiderTaskInfo.builder()
            .name(config.getName())
            .description(config.getDescription())
            .framework(config.getFramework().getValue())
            .threadCount(config.getThreadCount())
            .startUrlCount(config.getStartUrls() == null ? 0 : config.getStartUrls().size())
            .startUrls(config.getStartUrls())
            .pipelines(config.getPipelines())
            .status(runtimeState.getStatus())
            .lastStartTime(runtimeState.getLastStartTime())
            .lastEndTime(runtimeState.getLastEndTime())
            .lastDurationMillis(runtimeState.getLastDurationMillis())
            .lastErrorMessage(runtimeState.getLastErrorMessage())
            .build();
    }
}



