package com.example.hotdeal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueSseFanoutEvent {

    private Long hotDealId;
    private Long userId;
    private Long position;
    private boolean canPurchase;
}
