package com.example.media.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "media.url")
public class MediaUrlProperties {

    private MediaUrlAccessType accessType = MediaUrlAccessType.PUBLIC;
    private long signedUrlTtlSeconds = 300;
    private String defaultCacheControl = "public, max-age=604800, stale-while-revalidate=60";
    private String thumbnailCacheControl = "public, max-age=31536000, immutable";
}
