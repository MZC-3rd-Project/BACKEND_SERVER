package com.example.notification.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AuthEmailConfirmEventMessage {

    private String eventId;
    private String eventType;
    private String email;
    private String verificationCode;
}
