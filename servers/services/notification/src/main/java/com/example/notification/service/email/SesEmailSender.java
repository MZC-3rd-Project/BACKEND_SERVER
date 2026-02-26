package com.example.notification.service.email;

import com.example.notification.config.NotificationEmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SesEmailSender implements EmailSenderStrategy {

    private final SesClient sesClient;
    private final NotificationEmailProperties notificationEmailProperties;

    @Override
    public EmailProviderType providerType() {
        return EmailProviderType.SES;
    }

    @Override
    public EmailSendResult send(EmailSendCommand command) {
        try {
            Content subject = Content.builder()
                    .data(command.getSubject())
                    .charset("UTF-8")
                    .build();

            Body.Builder bodyBuilder = Body.builder();
            if (command.getTextBody() != null && !command.getTextBody().isBlank()) {
                bodyBuilder.text(Content.builder()
                        .data(command.getTextBody())
                        .charset("UTF-8")
                        .build());
            }
            if (command.getHtmlBody() != null && !command.getHtmlBody().isBlank()) {
                bodyBuilder.html(Content.builder()
                        .data(command.getHtmlBody())
                        .charset("UTF-8")
                        .build());
            }

            SendEmailRequest.Builder requestBuilder = SendEmailRequest.builder()
                    .source(notificationEmailProperties.getFromAddress())
                    .destination(Destination.builder().toAddresses(command.getTo()).build())
                    .message(Message.builder()
                            .subject(subject)
                            .body(bodyBuilder.build())
                            .build());

            String configurationSet = notificationEmailProperties.getConfigurationSet();
            if (configurationSet != null && !configurationSet.isBlank()) {
                requestBuilder.configurationSetName(configurationSet);
            }

            SendEmailResponse response = sesClient.sendEmail(requestBuilder.build());
            return EmailSendResult.success("SES", response.messageId());
        } catch (SesException e) {
            log.error("SES email send failed: to={}, subject={}", command.getTo(), command.getSubject(), e);
            return EmailSendResult.failure("SES", e.awsErrorDetails() == null
                    ? e.getMessage()
                    : e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("SES email send failed: to={}, subject={}", command.getTo(), command.getSubject(), e);
            return EmailSendResult.failure("SES", e.getMessage());
        }
    }
}
