package com.example.event.outbox;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OutboxSavedEvent extends ApplicationEvent {

    private final Long messageId;

    public OutboxSavedEvent(Long messageId) {
        super(messageId);
        this.messageId = messageId;
    }
}
