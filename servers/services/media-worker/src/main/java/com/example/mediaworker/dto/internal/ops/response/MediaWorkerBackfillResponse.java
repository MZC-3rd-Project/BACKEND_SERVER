package com.example.mediaworker.dto.internal.ops.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaWorkerBackfillResponse {

    private final Long fromMediaId;
    private final Long toMediaId;
    private final int requestedSize;
    private final String derivativeProfile;
    private final long scannedCount;
    private final long queuedCount;
    private final long existingCount;
}

