package com.example.config.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterStoreServiceTest {

    @Mock
    private DeadLetterMessageRepository deadLetterMessageRepository;

    @Mock
    private DeadLetterAlertPublisher deadLetterAlertPublisher;

    private DeadLetterStoreService deadLetterStoreService;

    @BeforeEach
    void setUp() {
        DeadLetterProperties properties = new DeadLetterProperties();
        properties.setConsumerFailureAlertThreshold(3);
        properties.setRetryFailureAlertThreshold(2);
        properties.setRetryInitialDelaySeconds(60);
        properties.setRetryBackoffMultiplier(2);

        deadLetterStoreService = new DeadLetterStoreService(
                deadLetterMessageRepository,
                deadLetterAlertPublisher,
                properties
        );

        when(deadLetterMessageRepository.save(any(DeadLetterMessage.class))).thenAnswer(invocation -> {
            DeadLetterMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                ReflectionTestUtils.setField(message, "id", 10L);
            }
            return message;
        });
    }

    @Test
    void store_publishesConsumerThresholdAlertWhenAttemptCountReached() {
        when(deadLetterAlertPublisher.publish(any(DeadLetterMessage.class), eq(DlqAlertType.CONSUMER_FAILURE_THRESHOLD_EXCEEDED)))
                .thenReturn(true);

        DeadLetterMessage message = deadLetterStoreService.store(
                "order-events",
                0,
                11L,
                "key-1",
                "{\"eventId\":\"evt-1\"}",
                "processing failed",
                "evt-1",
                "ORDER_CREATED",
                3
        );

        assertThat(message.getConsumerAttemptCount()).isEqualTo(3);
        assertThat(message.getNextRetryAt()).isAfter(LocalDateTime.now().minusSeconds(1));
        verify(deadLetterAlertPublisher).publish(message, DlqAlertType.CONSUMER_FAILURE_THRESHOLD_EXCEEDED);
    }

    @Test
    void store_existingActiveMessageIncrementsRetryCountAndPublishesRetryAlert() {
        DeadLetterMessage existing = DeadLetterMessage.create(
                "order-events",
                0,
                11L,
                "key-1",
                "{\"eventId\":\"evt-1\"}",
                "processing failed",
                "evt-1",
                "ORDER_CREATED",
                3,
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(existing, "id", 99L);
        existing.recordRetryFailure(
                "order-events",
                0,
                12L,
                "key-1",
                "{\"eventId\":\"evt-1\"}",
                "failed again",
                "ORDER_CREATED",
                3,
                LocalDateTime.now().plusSeconds(60)
        );

        when(deadLetterMessageRepository.findFirstByEventIdAndStatusInOrderByCreatedAtDesc(
                eq("evt-1"),
                eq(Set.of(DeadLetterMessage.DlqStatus.UNRESOLVED, DeadLetterMessage.DlqStatus.RETRYING))
        )).thenReturn(Optional.of(existing));
        when(deadLetterAlertPublisher.publish(any(DeadLetterMessage.class), eq(DlqAlertType.DLQ_RETRY_THRESHOLD_EXCEEDED)))
                .thenReturn(true);

        DeadLetterMessage updated = deadLetterStoreService.store(
                "order-events",
                0,
                13L,
                "key-1",
                "{\"eventId\":\"evt-1\"}",
                "failed third time",
                "evt-1",
                "ORDER_CREATED",
                3
        );

        assertThat(updated.getRetryCount()).isEqualTo(2);
        assertThat(updated.getOffset()).isEqualTo(13L);
        verify(deadLetterAlertPublisher).publish(updated, DlqAlertType.DLQ_RETRY_THRESHOLD_EXCEEDED);
    }

    @Test
    void store_skipsConsumerAlertWhenAttemptCountBelowThreshold() {
        deadLetterStoreService.store(
                "order-events",
                0,
                11L,
                "key-1",
                "{\"eventId\":\"evt-1\"}",
                "processing failed",
                "evt-1",
                "ORDER_CREATED",
                1
        );

        verify(deadLetterAlertPublisher, never()).publish(any(DeadLetterMessage.class), any(DlqAlertType.class));
    }
}
