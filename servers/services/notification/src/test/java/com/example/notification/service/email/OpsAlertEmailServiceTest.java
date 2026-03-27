package com.example.notification.service.email;

import com.example.notification.config.NotificationOpsAlertProperties;
import com.example.notification.consumer.ops.DlqOpsAlertEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsAlertEmailServiceTest {

    @Mock
    private EmailSender emailSender;

    private final NotificationOpsAlertProperties properties = new NotificationOpsAlertProperties();

    @Test
    void sendDlqAlert_sendsOpsMailWhenProviderSuccess() {
        properties.setToAddress("ddingsha9@teambind.co.kr");
        OpsAlertEmailService service = new OpsAlertEmailService(emailSender, properties);
        when(emailSender.send(any())).thenReturn(EmailSendResult.success("SMTP", "msg-ops-1"));

        DlqOpsAlertEventMessage event = eventMessage();
        service.sendDlqAlert(event);

        ArgumentCaptor<EmailSendCommand> captor = ArgumentCaptor.forClass(EmailSendCommand.class);
        verify(emailSender).send(captor.capture());
        EmailSendCommand command = captor.getValue();
        assertThat(command.getTo()).isEqualTo("ddingsha9@teambind.co.kr");
        assertThat(command.getSubject()).contains("DLQ ALERT");
        assertThat(command.getSubject()).contains("order-service");
        assertThat(command.getTextBody()).contains("consumerAttemptCount: 3");
        assertThat(command.getTextBody()).contains("dlqRetryCount: 2");
    }

    @Test
    void sendDlqAlert_throwsWhenProviderFailed() {
        OpsAlertEmailService service = new OpsAlertEmailService(emailSender, properties);
        when(emailSender.send(any())).thenReturn(EmailSendResult.failure("SMTP", "smtp unavailable"));

        assertThatThrownBy(() -> service.sendDlqAlert(eventMessage()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ops alert mail send failed");
    }

    private DlqOpsAlertEventMessage eventMessage() {
        DlqOpsAlertEventMessage event = new DlqOpsAlertEventMessage();
        ReflectionTestUtils.setField(event, "eventId", "evt-ops-1");
        ReflectionTestUtils.setField(event, "eventType", "DLQ_ALERT_REQUESTED");
        ReflectionTestUtils.setField(event, "serviceName", "order-service");
        ReflectionTestUtils.setField(event, "alertType", "DLQ_RETRY_THRESHOLD_EXCEEDED");
        ReflectionTestUtils.setField(event, "deadLetterId", 101L);
        ReflectionTestUtils.setField(event, "originalTopic", "order-events");
        ReflectionTestUtils.setField(event, "partition", 0);
        ReflectionTestUtils.setField(event, "offset", 44L);
        ReflectionTestUtils.setField(event, "eventIdValue", "evt-order-1");
        ReflectionTestUtils.setField(event, "eventTypeValue", "ORDER_CREATED");
        ReflectionTestUtils.setField(event, "consumerAttemptCount", 3);
        ReflectionTestUtils.setField(event, "dlqRetryCount", 2);
        ReflectionTestUtils.setField(event, "errorMessage", "processing failed");
        ReflectionTestUtils.setField(event, "payload", "{\"eventId\":\"evt-order-1\"}");
        return event;
    }
}
