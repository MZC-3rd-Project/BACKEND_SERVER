package com.example.mediaworker.dto.internal.ops.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MediaWorkerDlqItemResponse {

    private final Long dlqId;
    private final Long taskId;
    private final Long mediaId;
    private final String derivativeProfile;
    private final Long mediaVersion;
    private final Integer retryCount;
    private final String errorCode;
    private final String errorMessage;
    private final String sourceEventId;
    private final LocalDateTime createdAt;
}

