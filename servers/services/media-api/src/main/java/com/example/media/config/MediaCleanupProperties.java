package com.example.media.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "media.cleanup")
public class MediaCleanupProperties {

    private int batchSize = 100;
    private boolean deleteObjectEnabled = false;
}
