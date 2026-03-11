package com.example.event.inbox;

import com.example.event.consumer.EventMessageProcessor;

final class ProcessorBackedInboxEventHandler implements InboxEventHandler {

    private final String consumerName;
    private final EventMessageProcessor processor;

    ProcessorBackedInboxEventHandler(String consumerName, EventMessageProcessor processor) {
        this.consumerName = consumerName;
        this.processor = processor;
    }

    @Override
    public String consumerName() {
        return consumerName;
    }

    @Override
    public boolean supports(String eventType) {
        return processor.supports(eventType);
    }

    @Override
    public void handle(String eventId, String eventType, String payload) {
        processor.process(payload, eventId, eventType);
    }
}
