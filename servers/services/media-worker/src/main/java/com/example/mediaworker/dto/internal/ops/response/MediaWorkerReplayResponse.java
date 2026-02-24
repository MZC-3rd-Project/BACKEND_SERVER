package com.example.mediaworker.dto.internal.ops.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaWorkerReplayResponse {

    private final Long taskId;
    private final boolean queued;
    private final String previousStatus;
    private final String currentStatus;
    private final String message;
}

