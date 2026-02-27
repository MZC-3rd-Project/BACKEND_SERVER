package com.example.mediaworker.service;

import com.example.mediaworker.entity.MediaDerivativeTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "media.worker.derivative", name = "enabled", havingValue = "false")
public class NoopMediaDerivativeProcessor implements MediaDerivativeProcessor {

    @Override
    public void process(MediaDerivativeTask task) {
        log.info(
                "[MediaWorker] processed derivative task(no-op). taskId={}, mediaId={}, profile={}, version={}, retryCount={}",
                task.getId(),
                task.getMediaId(),
                task.getDerivativeProfile(),
                task.getMediaVersion(),
                task.getRetryCount()
        );
    }
}
