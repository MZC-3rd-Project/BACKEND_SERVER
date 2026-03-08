package com.example.notification.consumer.auth;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.notification.service.email.AuthEmailEventService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthEmailEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private AuthEmailEventService authEmailEventService;

    private AuthEmailEventProcessor authEmailEventProcessor;

    @BeforeEach
    void setUp() {
        authEmailEventProcessor = new AuthEmailEventProcessor(idempotentConsumerService, authEmailEventService);
        lenient().when(idempotentConsumerService.executeIdempotent(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    @Test
    void process_sendsEmailWhenEmailConfirmEvent() {
        String message = """
                {
                  "eventId": "evt-auth-1",
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com",
                  "verificationCode": "ABC123"
                }
                """;

        authEmailEventProcessor.process(message, "evt-auth-1", "EMAIL_CONFIRM_EVENT");

        verify(authEmailEventService).sendEmailConfirm("evt-auth-1", "user@example.com", "ABC123");
        verify(idempotentConsumerService).executeIdempotent(eq("evt-auth-1"), eq("AUTH_EVENT"), any());
    }

    @Test
    void process_skipsWhenEnvelopeInvalid() {
        String message = """
                {
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com",
                  "verificationCode": "ABC123"
                }
                """;

        authEmailEventProcessor.process(message, null, "EMAIL_CONFIRM_EVENT");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(authEmailEventService);
    }

    @Test
    void process_skipsWhenPayloadMissing() {
        String message = """
                {
                  "eventId": "evt-auth-2",
                  "eventType": "EMAIL_CONFIRM_EVENT",
                  "email": "user@example.com"
                }
                """;

        authEmailEventProcessor.process(message, "evt-auth-2", "EMAIL_CONFIRM_EVENT");

        verify(authEmailEventService, never()).sendEmailConfirm(any(), any(), any());
        verifyNoInteractions(idempotentConsumerService);
    }
}
