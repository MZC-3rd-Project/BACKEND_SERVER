package com.example.notification.service.email;

import com.example.notification.config.NotificationOpsAlertProperties;
import com.example.notification.consumer.ops.DlqOpsAlertEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAlertEmailService {

    private final EmailSender emailSender;
    private final NotificationOpsAlertProperties properties;

    public void sendDlqAlert(DlqOpsAlertEventMessage event) {
        if (!properties.isEnabled() || !hasText(properties.getToAddress())) {
            log.info("DLQ ops alert email skipped. enabled={}, toAddressPresent={}",
                    properties.isEnabled(), hasText(properties.getToAddress()));
            return;
        }

        EmailSendResult result = emailSender.send(EmailSendCommand.builder()
                .to(properties.getToAddress())
                .subject(buildSubject(event))
                .textBody(buildBody(event))
                .build());

        if (result.isSuccess()) {
            log.info("DLQ ops alert email sent. provider={}, alertType={}, serviceName={}",
                    result.getProvider(), event.getAlertType(), event.getServiceName());
            return;
        }

        String reason = result.getErrorMessage() == null ? "unknown" : result.getErrorMessage();
        throw new IllegalStateException("DLQ ops alert mail send failed: " + reason);
    }

    private String buildSubject(DlqOpsAlertEventMessage event) {
        String alertType = safeValue(event.getAlertType(), "UNKNOWN");
        String serviceName = safeValue(event.getServiceName(), "unknown-service");
        String topic = safeValue(event.getOriginalTopic(), "unknown-topic");
        return "[돈오아][DLQ ALERT][" + alertType + "] " + serviceName + " / " + topic;
    }

    private String buildBody(DlqOpsAlertEventMessage event) {
        String payload = truncate(event.getPayload(), Math.max(200, properties.getPayloadPreviewLength()));
        return """
                DLQ threshold alert has been raised.

                serviceName: %s
                alertType: %s
                deadLetterId: %s
                topic: %s
                partition: %s
                offset: %s
                key: %s
                eventId: %s
                eventType: %s
                consumerAttemptCount: %s
                dlqRetryCount: %s
                errorMessage: %s

                payloadPreview:
                %s
                """.formatted(
                safeValue(event.getServiceName(), "unknown-service"),
                safeValue(event.getAlertType(), "UNKNOWN"),
                safeValue(event.getDeadLetterId(), "-"),
                safeValue(event.getOriginalTopic(), "-"),
                safeValue(event.getPartition(), "-"),
                safeValue(event.getOffset(), "-"),
                safeValue(event.getKeyValue(), "-"),
                safeValue(event.getEventIdValue(), "-"),
                safeValue(event.getEventTypeValue(), "-"),
                safeValue(event.getConsumerAttemptCount(), "-"),
                safeValue(event.getDlqRetryCount(), "-"),
                safeValue(event.getErrorMessage(), "-"),
                payload == null ? "-" : payload
        );
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
