package com.example.media.dto.query.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class MediaUrlResponse {

    private Long mediaId;
    private String status;
    private String objectKey;
    private String mediaUrl;
    private String urlAccessType;
    private Instant urlExpiresAt;
    private String cacheControl;
    private String usageType;
}
