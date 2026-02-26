package com.example.notification.service.email;

public interface EmailSender {

    EmailSendResult send(EmailSendCommand command);
}
