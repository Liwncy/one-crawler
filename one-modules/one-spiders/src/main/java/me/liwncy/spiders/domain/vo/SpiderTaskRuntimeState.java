package me.liwncy.spiders.domain.vo;

import lombok.Builder;
import lombok.Value;
import me.liwncy.spiders.enums.SpiderTaskStatus;

import java.time.LocalDateTime;

/**
 * 蜘蛛任务运行时状态。
 */
@Value
@Builder
public class SpiderTaskRuntimeState {

    @Builder.Default
    SpiderTaskStatus status = SpiderTaskStatus.READY;

    LocalDateTime lastStartTime;

    LocalDateTime lastEndTime;

    Long lastDurationMillis;

    String lastErrorMessage;
}


