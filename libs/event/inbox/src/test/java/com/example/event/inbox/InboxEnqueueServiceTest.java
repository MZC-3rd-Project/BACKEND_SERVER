package com.example.event.inbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private ObjectProvider<InboxSignalPublisher> inboxSignalPublisherProvider;

    private InboxEnqueueService inboxEnqueueService;

    @BeforeEach
    void setUp() {
        InboxProperties inboxProperties = new InboxProperties();
        lenient().when(inboxSignalPublisherProvider.getIfAvailable()).thenReturn(inboxSignalPublisher);
        inboxEnqueueService = new InboxEnqueueService(
                inboxRepository,
                inboxSignalPublisherProvider,
                inboxProperties
        );
    }

    @Test
    void enqueue_savesAndSignalsWhenValid() {
        when(inboxRepository.insertPendingIgnoreDuplicate(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}",
                "PENDING"
        )).thenReturn(1);

        boolean result = inboxEnqueueService.enqueue(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}"
        );

        assertThat(result).isTrue();
        verify(inboxRepository).insertPendingIgnoreDuplicate(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}",
                "PENDING"
        );
        verify(inboxSignalPublisher).signal(eq("profile-user-events-consumer"));
    }

    @Test
    void enqueue_returnsFalseOnDuplicateEventConstraint() {
        when(inboxRepository.insertPendingIgnoreDuplicate(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}",
                "PENDING"
        )).thenReturn(0);

        boolean result = inboxEnqueueService.enqueue(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}"
        );

        assertThat(result).isFalse();
        verify(inboxRepository).insertPendingIgnoreDuplicate(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}",
                "PENDING"
        );
        verify(inboxSignalPublisher, never()).signal(any());
    }

    @Test
    void enqueue_rethrowsOnNonDuplicateIntegrityViolation() {
        when(inboxRepository.insertPendingIgnoreDuplicate(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}",
                "PENDING"
        )).thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> inboxEnqueueService.enqueue(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}"
        )).isInstanceOf(IllegalStateException.class);

        verify(inboxRepository).insertPendingIgnoreDuplicate(
                "profile-user-events-consumer",
                "evt-1",
                "UserCreated",
                "{\"eventId\":\"evt-1\"}",
                "PENDING"
        );
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
