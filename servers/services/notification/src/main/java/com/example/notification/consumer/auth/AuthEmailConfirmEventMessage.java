package com.example.notification.consumer.auth;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AuthEmailConfirmEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private String email;
    private String verificationCode;
}
