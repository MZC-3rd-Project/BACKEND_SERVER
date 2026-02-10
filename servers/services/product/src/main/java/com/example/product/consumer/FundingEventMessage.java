package com.example.product.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FundingEventMessage {

    private String eventId;
    private String eventType;
    private Long itemId;
}
