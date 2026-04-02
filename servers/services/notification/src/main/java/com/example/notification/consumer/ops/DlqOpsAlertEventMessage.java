package com.example.notification.consumer.ops;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DlqOpsAlertEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private String serviceName;
    private String alertType;
    private Long deadLetterId;
    private String originalTopic;
    private Integer partition;
    private Long offset;
    private String keyValue;
    private String eventIdValue;
    private String eventTypeValue;
    private Integer consumerAttemptCount;
    private Integer dlqRetryCount;
    private String errorMessage;
    private String payload;
}
