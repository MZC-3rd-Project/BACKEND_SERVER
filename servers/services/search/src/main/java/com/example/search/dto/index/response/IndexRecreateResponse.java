package com.example.search.dto.index.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class IndexRecreateResponse {

    private final String indexName;
    private final boolean existed;
    private final boolean recreated;
    private final Instant requestedAt;
}
