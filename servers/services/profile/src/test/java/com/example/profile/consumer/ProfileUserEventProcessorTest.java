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
class ProfileUserEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private ProfileProjectionSyncService profileProjectionSyncService;

    private ProfileUserEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ProfileUserEventProcessor(idempotentConsumerService, profileProjectionSyncService);
    }

    @Test
    void process_userCreated_runsProjectionSync() {
        String message = """
                {"eventId":"evt-1","eventType":"UserCreated","userId":101,"email":"user@example.com","nickname":"tester"}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("USER_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-1", ProfileUserEventProcessor.USER_CREATED_EVENT_TYPE);

        verify(profileProjectionSyncService).upsertFromUserCreated(101L, "user@example.com", "tester");
    }

    @Test
    void process_userEmailChanged_runsProjectionSync() {
        String message = """
                {"eventId":"evt-2","eventType":"UserEmailChanged","userId":101,"newEmail":"new@example.com"}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-2"), eq("USER_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-2", ProfileUserEventProcessor.USER_EMAIL_CHANGED_EVENT_TYPE);

        verify(profileProjectionSyncService).applyUserEmailChanged(101L, "new@example.com");
    }

    @Test
    void process_userWithdrawn_runsProjectionSync() {
        String message = """
                {"eventId":"evt-3","eventType":"UserWithdrawn","userId":101}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-3"), eq("USER_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-3", ProfileUserEventProcessor.USER_WITHDRAWN_EVENT_TYPE);

        verify(profileProjectionSyncService).withdrawProjection(101L);
    }

    @Test
    void process_skipsInvalidPayload() {
        String message = """
                {"eventId":"evt-4","eventType":"UserCreated","userId":101,"email":"user@example.com"}
                """;

        processor.process(message, "evt-4", ProfileUserEventProcessor.USER_CREATED_EVENT_TYPE);

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(profileProjectionSyncService);
    }

    @Test
    void process_ignoresUnsupportedEventType() {
        String message = """
                {"eventId":"evt-5","eventType":"UserPasswordChanged","userId":101}
                """;

        processor.process(message, "evt-5", "UserPasswordChanged");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(profileProjectionSyncService);
    }

    @Test
    void process_rethrowsWhenIdempotentFails() {
        String message = """
                {"eventId":"evt-6","eventType":"UserCreated","userId":101,"email":"user@example.com","nickname":"tester"}
                """;

        when(idempotentConsumerService.executeIdempotent(eq("evt-6"), eq("USER_EVENT"), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> processor.process(message, "evt-6", ProfileUserEventProcessor.USER_CREATED_EVENT_TYPE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");

        verify(profileProjectionSyncService, never()).upsertFromUserCreated(any(), any(), any());
    }
}
