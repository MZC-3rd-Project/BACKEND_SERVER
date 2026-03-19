package com.example.search.consumer.item;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long itemId;
    private String itemType;
    private String status;
    private Long sellerId;
    private Long storeId;
}
