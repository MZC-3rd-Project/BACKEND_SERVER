package com.example.search.dto.index.response;

import com.example.search.entity.SearchIndexingFailureStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IndexingFailureRetryResponse {

    private final Long failureId;
    private final String eventId;
    private final String eventType;
    private final SearchIndexingFailureStatus status;
    private final Integer retryCount;
    private final String failureReason;
    private final LocalDateTime lastRetriedAt;
}
