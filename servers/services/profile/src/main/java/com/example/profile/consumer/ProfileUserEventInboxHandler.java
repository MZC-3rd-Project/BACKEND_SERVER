package com.example.profile.consumer;

import com.example.event.inbox.InboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileUserEventInboxHandler implements InboxEventHandler {

    public static final String CONSUMER_NAME = "profile-user-events-consumer";

    private final ProfileUserEventProcessor profileUserEventProcessor;

    @Override
    public String consumerName() {
        return CONSUMER_NAME;
    }

    @Override
    public boolean supports(String eventType) {
        return profileUserEventProcessor.supports(eventType);
    }

    @Override
    public void handle(String eventId, String eventType, String payload) {
        profileUserEventProcessor.process(payload, eventId, eventType);
    }
}
