package com.example.mediaworker.dto.internal.ops.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaWorkerTaskSummaryResponse {

    private final long pendingCount;
    private final long processingCount;
    private final long completedCount;
    private final long failedCount;
    private final long dlqCount;
}

