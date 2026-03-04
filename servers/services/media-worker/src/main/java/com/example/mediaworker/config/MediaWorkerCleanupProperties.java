package com.example.mediaworker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "media.worker.cleanup")
public class MediaWorkerCleanupProperties {

    private boolean enabled = true;
    private int batchSize = 100;
    private long fixedDelayMs = 60_000L;
    private boolean deleteObjectEnabled = false;
}
