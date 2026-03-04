package com.example.profile.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.profile.service.command.ProfileProjectionSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private ProfileProjectionSyncService profileProjectionSyncService;

    private UserEventConsumer userEventConsumer;

    @BeforeEach
    void setUp() {
        userEventConsumer = new UserEventConsumer(idempotentConsumerService, profileProjectionSyncService);
    }

    @Test
    void consume_processesUserCreatedEventIdempotently() {
        String message = """
                {"eventId":"evt-1","eventType":"UserCreated","userId":101,"email":"user@example.com","nickname":"tester"}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("USER_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> processor = invocation.getArgument(2);
                    processor.get();
                    return Optional.empty();
                });

        userEventConsumer.consume(message);

        verify(profileProjectionSyncService).upsertFromUserCreated(101L, "user@example.com", "tester");
    }

    @Test
    void consume_skipsWhenEnvelopeIsInvalid() {
        String message = """
                {"eventType":"UserCreated","userId":101,"email":"user@example.com","nickname":"tester"}
                """;

        userEventConsumer.consume(message);

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(profileProjectionSyncService);
    }

    @Test
    void consume_skipsUnsupportedEventType() {
        String message = """
                {"eventId":"evt-unsupported","eventType":"UserPasswordChanged","userId":101}
                """;

        userEventConsumer.consume(message);

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(profileProjectionSyncService);
    }

    @Test
    void consume_processesUserEmailChangedEventIdempotently() {
        String message = """
                {"eventId":"evt-2","eventType":"UserEmailChanged","userId":101,"oldEmail":"old@example.com","newEmail":"new@example.com"}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-2"), eq("USER_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> processor = invocation.getArgument(2);
                    processor.get();
                    return Optional.empty();
                });

        userEventConsumer.consume(message);

        verify(profileProjectionSyncService).applyUserEmailChanged(101L, "new@example.com");
    }

    @Test
    void consume_processesUserWithdrawnEventIdempotently() {
        String message = """
                {"eventId":"evt-5","eventType":"UserWithdrawn","userId":101}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-5"), eq("USER_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> processor = invocation.getArgument(2);
                    processor.get();
                    return Optional.empty();
                });

        userEventConsumer.consume(message);

        verify(profileProjectionSyncService).withdrawProjection(101L);
    }

    @Test
    void consume_skipsWhenPayloadIsInvalid() {
        String message = """
                {"eventId":"evt-3","eventType":"UserCreated","userId":101,"email":"user@example.com"}
                """;

        userEventConsumer.consume(message);

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(profileProjectionSyncService);
    }

    @Test
    void consume_skipsWhenUserEmailChangedPayloadIsInvalid() {
        String message = """
                {"eventId":"evt-6","eventType":"UserEmailChanged","userId":101}
                """;

        userEventConsumer.consume(message);

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(profileProjectionSyncService);
    }

    @Test
    void consume_rethrowsWhenIdempotentProcessingFails() {
        String message = """
                {"eventId":"evt-4","eventType":"UserCreated","userId":101,"email":"user@example.com","nickname":"tester"}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-4"), eq("USER_EVENT"), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> userEventConsumer.consume(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");

        verify(profileProjectionSyncService, never()).upsertFromUserCreated(any(), any(), any());
    }
}
