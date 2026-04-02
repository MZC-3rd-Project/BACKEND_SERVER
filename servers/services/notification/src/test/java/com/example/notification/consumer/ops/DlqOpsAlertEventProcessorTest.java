package com.example.notification.consumer.ops;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.notification.service.email.OpsAlertEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DlqOpsAlertEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private OpsAlertEmailService opsAlertEmailService;

    private DlqOpsAlertEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DlqOpsAlertEventProcessor(idempotentConsumerService, opsAlertEmailService);
        lenient().when(idempotentConsumerService.executeIdempotent(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void process_sendsOpsAlertEmailWhenPayloadValid() {
        String message = """
                {
                  "eventId": "evt-ops-1",
                  "eventType": "DLQ_ALERT_REQUESTED",
                  "serviceName": "order-service",
                  "alertType": "DLQ_RETRY_THRESHOLD_EXCEEDED",
                  "deadLetterId": 101,
                  "originalTopic": "order-events"
                }
                """;

        processor.process(message, "evt-ops-1", "DLQ_ALERT_REQUESTED");

        verify(opsAlertEmailService).sendDlqAlert(any(DlqOpsAlertEventMessage.class));
        verify(idempotentConsumerService).executeIdempotent(eq("evt-ops-1"), eq("OPS_ALERT_EVENT"), any());
    }

    @Test
    void process_skipsWhenPayloadMissingRequiredFields() {
        String message = """
                {
                  "eventId": "evt-ops-2",
                  "eventType": "DLQ_ALERT_REQUESTED",
                  "alertType": "DLQ_RETRY_THRESHOLD_EXCEEDED"
                }
                """;

        processor.process(message, "evt-ops-2", "DLQ_ALERT_REQUESTED");

        verify(opsAlertEmailService, never()).sendDlqAlert(any(DlqOpsAlertEventMessage.class));
        verifyNoInteractions(idempotentConsumerService);
    }
}
