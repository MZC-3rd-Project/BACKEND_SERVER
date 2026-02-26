package com.example.mediaworker.dto.internal.ops.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MediaWorkerDlqListResponse {

    private final int size;
    private final List<MediaWorkerDlqItemResponse> items;
}

