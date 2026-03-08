package com.example.event.inbox;

import com.example.event.consumer.AbstractEnvelopeRoutingConsumer;
import com.example.event.consumer.EventMessageProcessor;
import com.example.event.consumer.JsonEventEnvelope;

public abstract class AbstractProcessorRoutingConsumer extends AbstractEnvelopeRoutingConsumer {

    private final InboxRoutingSupport inboxRoutingSupport;
    private final EventMessageProcessor eventMessageProcessor;

    protected AbstractProcessorRoutingConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            EventMessageProcessor eventMessageProcessor
    ) {
        this.inboxRoutingSupport = inboxRoutingSupport;
        this.eventMessageProcessor = eventMessageProcessor;
    }

    @Override
    protected final boolean supports(String eventType) {
        return eventMessageProcessor.supports(eventType);
    }

    @Override
    protected final boolean routeToInbox(JsonEventEnvelope envelope) {
        return inboxRoutingSupport.isInbox(getClass());
    }

    @Override
    protected final boolean enqueue(JsonEventEnvelope envelope, String message) {
        return inboxRoutingSupport.enqueue(getClass(), envelope, message);
    }

    @Override
    protected final void handleDirect(String message, JsonEventEnvelope envelope) {
        eventMessageProcessor.process(message, envelope.getEventId(), envelope.getEventType());
    }
}
