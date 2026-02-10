package com.example.stock.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemEventMessage {

    private String eventId;
    private String eventType;
    private Long itemId;
    private String itemType;
    private String title;
}
