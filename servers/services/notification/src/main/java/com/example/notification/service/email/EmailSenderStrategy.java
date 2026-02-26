package com.example.notification.service.email;

public interface EmailSenderStrategy {

    EmailProviderType providerType();

    EmailSendResult send(EmailSendCommand command);
}
