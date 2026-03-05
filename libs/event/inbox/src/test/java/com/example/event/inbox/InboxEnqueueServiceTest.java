package com.example.event.inbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxEnqueueServiceTest {

    @Mock
    private InboxRepository inboxRepository;

    @Mock
    private InboxSignalPublisher inboxSignalPublisher;

    private InboxEnqueueService inboxEnqueueService;

    @BeforeEach
    void setUp() {
        InboxProperties inboxProperties = new InboxProperties();
        inboxEnqueueService = new InboxEnqueueService(
                inboxRepository,
                inboxSignalPublisher,
                inboxProperties
        );
    }

    @Test
    void enqueue_savesAndSignalsWhenValid() {
        when(inboxRepository.save(any(InboxMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = inboxEnqueueService.enqueue(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}"
        );

        assertThat(result).isTrue();
        verify(inboxRepository).save(any(InboxMessage.class));
        verify(inboxSignalPublisher).signal(eq("profile-user-events-consumer"));
    }

    @Test
    void enqueue_returnsFalseOnDuplicateEventConstraint() {
        when(inboxRepository.save(any(InboxMessage.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate",
                        new SQLException(
                                "duplicate key value violates unique constraint \"uk_inbox_consumer_event\"",
                                "23505"
                        )
                ));

        boolean result = inboxEnqueueService.enqueue(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}"
        );

        assertThat(result).isFalse();
        verify(inboxRepository).save(any(InboxMessage.class));
        verify(inboxSignalPublisher, never()).signal(any());
    }

    @Test
    void enqueue_rethrowsOnNonDuplicateIntegrityViolation() {
        when(inboxRepository.save(any(InboxMessage.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "not-null violation",
                        new SQLException(
                                "null value in column payload violates not-null constraint",
                                "23502"
                        )
                ));

        assertThatThrownBy(() -> inboxEnqueueService.enqueue(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}"
        )).isInstanceOf(DataIntegrityViolationException.class);

        verify(inboxRepository).save(any(InboxMessage.class));
        verify(inboxSignalPublisher, never()).signal(any());
    }

    @Test
    void enqueue_returnsFalseWhenRequiredFieldIsBlank() {
        boolean result = inboxEnqueueService.enqueue(
                "profile-user-events-consumer",
                " ",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}"
        );

        assertThat(result).isFalse();
        verifyNoInteractions(inboxRepository);
        verifyNoInteractions(inboxSignalPublisher);
    }
}
