package com.example.mediaworker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "media.worker.tasks")
public class MediaWorkerTaskProperties {

    private int batchSize = 20;
    private int maxRetryCount = 5;
    private long dispatchFixedDelayMs = 3_000L;
    private long staleRecoveryFixedDelayMs = 30_000L;
    private long staleProcessingSeconds = 180L;
    private long retryInitialDelaySeconds = 5L;
    private long retryMaxDelaySeconds = 300L;
}
