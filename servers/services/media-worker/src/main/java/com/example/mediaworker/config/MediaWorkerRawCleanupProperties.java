package com.example.mediaworker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "media.worker.raw-cleanup")
public class MediaWorkerRawCleanupProperties {

    private boolean enabled = true;
    private int batchSize = 100;
    private long fixedDelayMs = 3_600_000L;
    private long transitionGraceDays = 7L;
    private boolean deleteObjectEnabled = false;
}
