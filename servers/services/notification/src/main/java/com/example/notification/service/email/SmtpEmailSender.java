package com.example.notification.service.email;

import com.example.notification.config.NotificationEmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSenderStrategy {

    private final JavaMailSender javaMailSender;
    private final NotificationEmailProperties notificationEmailProperties;

    @Override
    public EmailProviderType providerType() {
        return EmailProviderType.SMTP;
    }

    @Override
    public EmailSendResult send(EmailSendCommand command) {
        try {
            var mimeMessage = javaMailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setTo(command.getTo());
            helper.setSubject(command.getSubject());
            if (notificationEmailProperties.getFromAddress() != null
                    && !notificationEmailProperties.getFromAddress().isBlank()) {
                helper.setFrom(notificationEmailProperties.getFromAddress());
            }

            String textBody = command.getTextBody() == null ? "" : command.getTextBody();
            if (command.getHtmlBody() != null && !command.getHtmlBody().isBlank()) {
                helper.setText(textBody, command.getHtmlBody());
            } else {
                helper.setText(textBody, false);
            }

            javaMailSender.send(mimeMessage);
            return EmailSendResult.success("SMTP", mimeMessage.getMessageID());
        } catch (Exception e) {
            log.error("SMTP email send failed: to={}, subject={}", command.getTo(), command.getSubject(), e);
            return EmailSendResult.failure("SMTP", e.getMessage());
        }
    }
}
