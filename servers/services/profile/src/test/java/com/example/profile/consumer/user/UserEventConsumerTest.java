package com.example.profile.consumer.user;

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
class UserEventConsumerTest {

    @Mock
    private InboxEnqueueService inboxEnqueueService;

    @Mock
    private ProfileUserEventProcessor profileUserEventProcessor;

    private EventConsumerRoutingProperties routingProperties;
    private UserEventConsumer userEventConsumer;

    private ConsumerRecord<String, Object> recordOf(Object value) {
        return new ConsumerRecord<>("user-events", 0, 0L, null, value);
    }

    @BeforeEach
    void setUp() {
        routingProperties = new EventConsumerRoutingProperties();
        userEventConsumer = new UserEventConsumer(
                new InboxRoutingSupport(inboxEnqueueService, new EventConsumerRoutingResolver(routingProperties)),
                profileUserEventProcessor
        );
    }

    @Test
    void consume_directMode_dispatchesToProcessor() {
        String message = """
                {"eventId":"evt-1","eventType":"UserCreated","userId":101,"email":"user@example.com","nickname":"tester"}
                """;
        when(profileUserEventProcessor.supports("UserCreated")).thenReturn(true);

        userEventConsumer.consume(recordOf(message));

        verify(profileUserEventProcessor).process(eq(message), eq("evt-1"), eq("UserCreated"));
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_inboxMode_enqueuesMessage() {
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.INBOX);
        routingProperties.getRouting().put(ProfileUserEventProcessor.CONSUMER_NAME, routing);
        String message = """
                {"eventId":"evt-2","eventType":"UserEmailChanged","userId":101,"newEmail":"new@example.com"}
                """;
        when(profileUserEventProcessor.supports("UserEmailChanged")).thenReturn(true);

        userEventConsumer.consume(recordOf(message));

        verify(inboxEnqueueService).enqueue(
                ProfileUserEventProcessor.CONSUMER_NAME,
                "evt-2",
                "UserEmailChanged",
                message
        );
        verify(profileUserEventProcessor, never()).process(message, "evt-2", "UserEmailChanged");
    }

    @Test
    void consume_skipsInvalidEnvelope() {
        String message = """
                {"eventType":"UserCreated","userId":101}
                """;

        userEventConsumer.consume(recordOf(message));

        verifyNoInteractions(profileUserEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_skipsMalformedPayload() {
        String malformed = "not-json";

        userEventConsumer.consume(recordOf(malformed));

        verifyNoInteractions(profileUserEventProcessor);
        verifyNoInteractions(inboxEnqueueService);
    }

    @Test
    void consume_skipsUnsupportedType() {
        String message = """
                {"eventId":"evt-3","eventType":"UserPasswordChanged","userId":101}
                """;
        when(profileUserEventProcessor.supports("UserPasswordChanged")).thenReturn(false);

        userEventConsumer.consume(recordOf(message));

        verify(profileUserEventProcessor).supports("UserPasswordChanged");
        verify(profileUserEventProcessor, never()).process(message, "evt-3", "UserPasswordChanged");
        verifyNoInteractions(inboxEnqueueService);
    }
}
