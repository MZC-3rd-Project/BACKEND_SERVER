package com.example.mediaworker.dto.internal.ops.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MediaWorkerBackfillRequest {

    @Min(value = 1, message = "fromMediaId must be positive")
    private Long fromMediaId;

    @Min(value = 1, message = "toMediaId must be positive")
    private Long toMediaId;

    @Min(value = 1, message = "size must be at least 1")
    private Integer size;

    private String derivativeProfile;
}

