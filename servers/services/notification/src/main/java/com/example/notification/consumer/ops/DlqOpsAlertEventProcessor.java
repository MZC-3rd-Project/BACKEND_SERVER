package com.example.notification.consumer.ops;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.notification.service.email.OpsAlertEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = DlqOpsAlertEventProcessor.CONSUMER_NAME)
public class DlqOpsAlertEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-ops-alert-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "OPS_ALERT_EVENT";
    private static final String DLQ_ALERT_EVENT_TYPE = "DLQ_ALERT_REQUESTED";

    private final Map<String, EventSpec<DlqOpsAlertEventMessage>> eventSpecs;

    public DlqOpsAlertEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            OpsAlertEmailService opsAlertEmailService
    ) {
        super(idempotentConsumerService);
        this.eventSpecs = Map.of(
                DLQ_ALERT_EVENT_TYPE,
                EventSpec.of(
                        DlqOpsAlertEventMessage.class,
                        this::hasRequiredFields,
                        event -> {
                            opsAlertEmailService.sendDlqAlert(event);
                            log.info("DLQ ops alert processed. eventId={}, deadLetterId={}",
                                    event.getEventId(), event.getDeadLetterId());
                        }
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<DlqOpsAlertEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid ops-alert-events message. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        DlqOpsAlertEventMessage alertEvent = (DlqOpsAlertEventMessage) event;
        log.warn("Skip DLQ ops alert due to missing payload. eventId={}, deadLetterId={}",
                eventId, alertEvent == null ? null : alertEvent.getDeadLetterId());
    }

    private boolean hasRequiredFields(DlqOpsAlertEventMessage event) {
        return event.getDeadLetterId() != null
                && hasText(event.getAlertType())
                && hasText(event.getServiceName())
                && hasText(event.getOriginalTopic());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
