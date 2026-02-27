package com.example.notification.service.email;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailSendCommand {

    private String to;
    private String subject;
    private String textBody;
    private String htmlBody;
}
