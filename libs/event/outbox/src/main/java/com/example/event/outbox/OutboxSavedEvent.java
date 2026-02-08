package com.example.event.outbox;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OutboxSavedEvent extends ApplicationEvent {

    private final OutboxMessage outboxMessage;

    public OutboxSavedEvent(OutboxMessage outboxMessage) {
        super(outboxMessage);
        this.outboxMessage = outboxMessage;
    }
}
