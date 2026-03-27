package com.example.notification.consumer.ops;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.EventConsumerRoutingProperties;
import com.example.event.consumer.EventConsumerRoutingResolver;
import com.example.event.inbox.InboxEnqueueService;
import com.example.event.inbox.InboxRoutingSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqOpsAlertEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private DlqOpsAlertEventProcessor processor;

    private DlqOpsAlertEventConsumer consumer;
    private EventConsumerRoutingProperties routingProperties;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new DlqOpsAlertEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                processor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
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
        when(processor.supports("DLQ_ALERT_REQUESTED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(processor).process(eq(message), eq("evt-ops-1"), eq("DLQ_ALERT_REQUESTED"));
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {
                  "eventId": "evt-ops-2",
                  "eventType": "DLQ_ALERT_REQUESTED",
                  "serviceName": "order-service",
                  "alertType": "CONSUMER_FAILURE_THRESHOLD_EXCEEDED",
                  "deadLetterId": 102,
                  "originalTopic": "order-events"
                }
                """;
        when(processor.supports("DLQ_ALERT_REQUESTED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                DlqOpsAlertEventProcessor.CONSUMER_NAME,
                "evt-ops-2",
                "DLQ_ALERT_REQUESTED",
                message
        )).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                DlqOpsAlertEventProcessor.CONSUMER_NAME,
                "evt-ops-2",
                "DLQ_ALERT_REQUESTED",
                message
        );
        verify(processor, never()).process(message, "evt-ops-2", "DLQ_ALERT_REQUESTED");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("ops-alert-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(DlqOpsAlertEventProcessor.CONSUMER_NAME, routing);
    }
}
