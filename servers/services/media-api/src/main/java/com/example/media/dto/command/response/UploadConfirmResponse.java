package com.example.media.dto.command.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class UploadConfirmResponse {

    private Long mediaId;
    private String status;
    private String objectKey;
    private Long fileSize;
    private String contentType;
    private String etag;
    private String mediaUrl;
    private String urlAccessType;
    private Instant urlExpiresAt;
    private String cacheControl;
    private Long linkId;
    private String ownerType;
    private Long ownerId;
    private String usageType;
    private Integer sortOrder;
}
