package com.example.notification.dto.command.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReadAllResultResponse {

    private int updatedCount;

    public static ReadAllResultResponse of(int updatedCount) {
        return ReadAllResultResponse.builder()
                .updatedCount(updatedCount)
                .build();
    }
}
