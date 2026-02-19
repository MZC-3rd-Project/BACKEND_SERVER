package com.example.notification.service.email;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailSendResult {

    private boolean success;
    private boolean skipped;
    private String provider;
    private String messageId;
    private String errorMessage;

    public static EmailSendResult success(String provider, String messageId) {
        return EmailSendResult.builder()
                .success(true)
                .skipped(false)
                .provider(provider)
                .messageId(messageId)
                .build();
    }

    public static EmailSendResult failure(String provider, String errorMessage) {
        return EmailSendResult.builder()
                .success(false)
                .skipped(false)
                .provider(provider)
                .errorMessage(errorMessage)
                .build();
    }

    public static EmailSendResult skipped(String provider, String reason) {
        return EmailSendResult.builder()
                .success(false)
                .skipped(true)
                .provider(provider)
                .errorMessage(reason)
                .build();
    }
}
