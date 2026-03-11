package com.example.notification.consumer.auth;

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
class AuthEmailEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private AuthEmailEventProcessor authEmailEventProcessor;

    private AuthEmailEventConsumer authEmailEventConsumer;
    private EventConsumerRoutingProperties routingProperties;

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        authEmailEventConsumer = new AuthEmailEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                authEmailEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "evt-auth-1",
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com",
                  "verificationCode": "ABC123"
                }
                """;
        when(authEmailEventProcessor.supports("EMAIL_CONFIRM_EVENT")).thenReturn(true);

        authEmailEventConsumer.consume(recordOf(message));

        verify(authEmailEventProcessor).process(eq(message), eq("evt-auth-1"), eq("EMAIL_CONFIRM_EVENT"));
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        String message = """
                {
                  "eventId": "evt-auth-2",
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com",
                  "verificationCode": "ABC123"
                }
                """;
        when(authEmailEventProcessor.supports("EMAIL_CONFIRM_EVENT")).thenReturn(true);
        when(inboxEnqueueService.enqueue(
                AuthEmailEventProcessor.CONSUMER_NAME,
                "evt-auth-2",
                "EMAIL_CONFIRM_EVENT",
                message
        )).thenReturn(true);

        authEmailEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                AuthEmailEventProcessor.CONSUMER_NAME,
                "evt-auth-2",
                "EMAIL_CONFIRM_EVENT",
                message
        );
        verify(authEmailEventProcessor, never()).process(message, "evt-auth-2", "EMAIL_CONFIRM_EVENT");
    }

    @Test
    void consume_skipsUnsupportedType() {
        configureDirectMode();
        String message = """
                {
                  "eventId": "evt-auth-3",
                  "eventType": "USER_CREATED"
                }
                """;
        when(authEmailEventProcessor.supports("USER_CREATED")).thenReturn(false);

        authEmailEventConsumer.consume(recordOf(message));

        verify(authEmailEventProcessor).supports("USER_CREATED");
        verify(authEmailEventProcessor, never()).process(message, "evt-auth-3", "USER_CREATED");
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        configureDirectMode();
        String message = """
                {
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com"
                }
                """;

        authEmailEventConsumer.consume(recordOf(message));

        verifyNoInteractions(authEmailEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("auth-events", 0, 0L, null, value);
    }

    private void configureDirectMode() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        routingProperties.getRouting().put(AuthEmailEventProcessor.CONSUMER_NAME, routing);
    }
}
