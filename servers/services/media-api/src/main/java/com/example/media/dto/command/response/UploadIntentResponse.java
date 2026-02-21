package com.example.media.dto.command.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class UploadIntentResponse {

    private Long mediaId;
    private String presignedUrl;
    private String objectKey;
    private String uploadToken;
    private Instant expiresAt;
    private String ownerType;
    private Long ownerId;
    private String usageType;
    private Integer sortOrder;
}
