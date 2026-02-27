package com.example.notification.service.email;

import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationType;
import com.example.notification.service.setting.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEmailService {

    private final EmailSender emailSender;
    private final NotificationSettingService notificationSettingService;

    public EmailSendResult send(Long userId,
                                NotificationType type,
                                String to,
                                String subject,
                                String textBody,
                                String htmlBody) {
        boolean allowed = notificationSettingService.shouldSendNotification(
                userId, type, NotificationChannel.EMAIL
        );
        if (!allowed) {
            log.info("Email notification skipped by user settings. userId={}, type={}", userId, type);
            return EmailSendResult.skipped("POLICY", "disabled by user preference");
        }

        return emailSender.send(EmailSendCommand.builder()
                .to(to)
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .build());
    }
}
