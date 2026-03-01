package com.example.hotdeal.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueueStreamEventResponse {

    private Long hotDealId;
    private Long position;
    private boolean canPurchase;
    private String serverTime;
}
