package me.liwncy.common.webmagic.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "webmagic")
public class WebMagicProperties {
    private int threadCount;
    private int timeout;
    private int retryTimes;
    private long sleepTime;
    private boolean randomUserAgent;
}
