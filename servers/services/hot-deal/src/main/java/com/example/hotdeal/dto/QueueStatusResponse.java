package com.example.hotdeal.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueueStatusResponse {

    private Long position;
    private boolean canPurchase;
}
