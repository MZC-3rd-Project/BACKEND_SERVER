package com.example.mediaworker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "media.s3")
public class MediaWorkerS3Properties {

    private String region = "ap-northeast-2";
    private String bucket = "team2-donmoa-media-raw";
    private String keyPrefix = "team2-donmoa-media";
    private String cloudfrontDomain;
}
