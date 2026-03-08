package com.example.notification.consumer.chat;

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
class ChatNotificationEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private ChatNotificationEventProcessor chatNotificationEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private ChatNotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        consumer = new ChatNotificationEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                chatNotificationEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {"eventId":"evt-chat-1","eventType":"CHAT_NOTIFICATION_REQUESTED","recipientId":5001,"roomId":7001}
                """;
        when(chatNotificationEventProcessor.supports("CHAT_NOTIFICATION_REQUESTED")).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(chatNotificationEventProcessor).process(
                eq(message),
                eq("evt-chat-1"),
                eq("CHAT_NOTIFICATION_REQUESTED")
        );
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {"eventId":"evt-chat-2","eventType":"CHAT_NOTIFICATION_REQUESTED","recipientId":5001,"roomId":7001}
                """;
        when(chatNotificationEventProcessor.supports("CHAT_NOTIFICATION_REQUESTED")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                ChatNotificationEventProcessor.CONSUMER_NAME,
                "evt-chat-2",
                "CHAT_NOTIFICATION_REQUESTED",
                message
        )).thenReturn(true);

        consumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                ChatNotificationEventProcessor.CONSUMER_NAME,
                "evt-chat-2",
                "CHAT_NOTIFICATION_REQUESTED",
                message
        );
        verify(chatNotificationEventProcessor, never()).process(message, "evt-chat-2", "CHAT_NOTIFICATION_REQUESTED");
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("chat-notification-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(ChatNotificationEventProcessor.CONSUMER_NAME, routing);
    }
}
