package com.example.mediaworker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "media.worker.derivative")
public class MediaWorkerDerivativeProperties {

    private boolean enabled = true;
    private int thumbnailMaxWidth = 640;
    private int thumbnailMaxHeight = 640;
    private float webpQuality = 0.82f;
}
