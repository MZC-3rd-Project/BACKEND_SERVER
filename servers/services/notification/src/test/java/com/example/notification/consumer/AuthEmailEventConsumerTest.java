package com.example.notification.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.notification.service.email.AuthEmailEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthEmailEventConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private AuthEmailEventService authEmailEventService;

    @InjectMocks
    private AuthEmailEventConsumer authEmailEventConsumer;

    @BeforeEach
    void setUp() {
        lenient().when(idempotentConsumerService.executeIdempotent(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void consumeAuth_sendsEmailWhenEmailConfirmEvent() {
        String message = """
                {
                  "eventId": "evt-auth-1",
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com",
                  "verificationCode": "ABC123"
                }
                """;

        authEmailEventConsumer.consumeAuth(message);

        verify(authEmailEventService).sendEmailConfirm("evt-auth-1", "user@example.com", "ABC123");
    }

    @Test
    void consumeAuth_ignoresUnknownAuthEventType() {
        String message = """
                {
                  "eventId": "evt-auth-2",
                  "eventType": "USER_CREATED",
                  "email": "user@example.com",
                  "verificationCode": "ABC123"
                }
                """;

        authEmailEventConsumer.consumeAuth(message);

        verify(authEmailEventService, never()).sendEmailConfirm(anyString(), anyString(), anyString());
    }

    @Test
    void consumeAuth_skipsWhenEnvelopeInvalid() {
        String message = """
                {
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com",
                  "verificationCode": "ABC123"
                }
                """;

        authEmailEventConsumer.consumeAuth(message);

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(authEmailEventService, never()).sendEmailConfirm(anyString(), anyString(), anyString());
    }

    @Test
    void consumeAuth_skipsWhenPayloadMissing() {
        String message = """
                {
                  "eventId": "evt-auth-3",
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com"
                }
                """;

        authEmailEventConsumer.consumeAuth(message);

        verify(authEmailEventService, never()).sendEmailConfirm(anyString(), anyString(), anyString());
    }
}
