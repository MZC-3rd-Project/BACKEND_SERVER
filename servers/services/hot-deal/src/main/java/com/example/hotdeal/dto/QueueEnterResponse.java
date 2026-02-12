package com.example.hotdeal.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueueEnterResponse {

    private String token;
    private Long position;
    private Long estimatedWaitSeconds;
}
