package com.example.search.consumer.store;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StoreEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long storeId;
    private Long userId;
    private String storeName;
    private String status;
}
