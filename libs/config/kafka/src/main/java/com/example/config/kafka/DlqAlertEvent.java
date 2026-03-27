package com.example.config.kafka;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class DlqAlertEvent extends DomainEvent {

    private static final String TOPIC = "ops-alert-events";
    private static final String EVENT_TYPE = "DLQ_ALERT_REQUESTED";

    private final String serviceName;
    private final String alertType;
    private final Long deadLetterId;
    private final String originalTopic;
    private final Integer partition;
    private final Long offset;
    private final String keyValue;
    private final String eventIdValue;
    private final String eventTypeValue;
    private final int consumerAttemptCount;
    private final int dlqRetryCount;
    private final String errorMessage;
    private final String payload;

    public DlqAlertEvent(String serviceName, DlqAlertType alertType, DeadLetterMessage deadLetterMessage) {
        super(TOPIC);
        this.serviceName = serviceName;
        this.alertType = alertType.name();
        this.deadLetterId = deadLetterMessage.getId();
        this.originalTopic = deadLetterMessage.getTopic();
        this.partition = deadLetterMessage.getPartition();
        this.offset = deadLetterMessage.getOffset();
        this.keyValue = deadLetterMessage.getKey();
        this.eventIdValue = deadLetterMessage.getEventId();
        this.eventTypeValue = deadLetterMessage.getEventType();
        this.consumerAttemptCount = deadLetterMessage.getConsumerAttemptCount();
        this.dlqRetryCount = deadLetterMessage.getRetryCount();
        this.errorMessage = deadLetterMessage.getErrorMessage();
        this.payload = deadLetterMessage.getPayload();
    }

    @Override
    public String getEventTypeName() {
        return EVENT_TYPE;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("serviceName", serviceName);
        payloadMap.put("alertType", alertType);
        payloadMap.put("deadLetterId", deadLetterId);
        payloadMap.put("originalTopic", originalTopic);
        payloadMap.put("partition", partition);
        payloadMap.put("offset", offset);
        payloadMap.put("keyValue", keyValue);
        payloadMap.put("eventIdValue", eventIdValue);
        payloadMap.put("eventTypeValue", eventTypeValue);
        payloadMap.put("consumerAttemptCount", consumerAttemptCount);
        payloadMap.put("dlqRetryCount", dlqRetryCount);
        payloadMap.put("errorMessage", errorMessage);
        payloadMap.put("payload", payload);
        return payloadMap;
    }
}
