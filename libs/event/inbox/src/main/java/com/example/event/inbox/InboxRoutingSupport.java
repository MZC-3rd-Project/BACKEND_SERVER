package com.example.event.inbox;

import com.example.event.consumer.EventConsumerRoutingResolver;
import com.example.event.consumer.JsonEventEnvelope;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InboxRoutingSupport {

    private final InboxEnqueueService inboxEnqueueService;
    private final EventConsumerRoutingResolver eventConsumerRoutingResolver;

    public boolean isInbox(Class<?> consumerType) {
        return eventConsumerRoutingResolver.isInbox(consumerType);
    }

    public boolean enqueue(Class<?> consumerType, JsonEventEnvelope envelope, String message) {
        return inboxEnqueueService.enqueue(
                eventConsumerRoutingResolver.consumerName(consumerType),
                envelope.getEventId(),
                envelope.getEventType(),
                message
        );
    }
}
