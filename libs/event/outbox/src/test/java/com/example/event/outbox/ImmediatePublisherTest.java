package com.example.event.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImmediatePublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private ImmediatePublisher immediatePublisher;

    @Test
    void handleOutboxSaved_skipsWhenMessageAlreadyClaimed() {
        when(outboxRepository.updateStatusById(1L, OutboxStatus.PENDING, OutboxStatus.SENDING)).thenReturn(0);

        immediatePublisher.handleOutboxSaved(new OutboxSavedEvent(1L));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void handleOutboxSaved_marksPublishedWhenSendSucceeds() {
        OutboxMessage message = createMessage(1L);
        when(outboxRepository.updateStatusById(1L, OutboxStatus.PENDING, OutboxStatus.SENDING)).thenReturn(1);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(message));
        CompletableFuture<SendResult<String, Object>> successFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(message.getTopic(), message.getAggregateId(), message.getPayload()))
                .thenReturn(successFuture);
        when(outboxRepository.markAsPublishedById(
                eq(1L), eq(OutboxStatus.SENDING), eq(OutboxStatus.PUBLISHED), any(LocalDateTime.class)))
                .thenReturn(1);

        immediatePublisher.handleOutboxSaved(new OutboxSavedEvent(1L));

        verify(outboxRepository).markAsPublishedById(
                eq(1L), eq(OutboxStatus.SENDING), eq(OutboxStatus.PUBLISHED), any(LocalDateTime.class));
    }

    @Test
    void handleOutboxSaved_revertsToPendingWhenSendFails() {
        OutboxMessage message = createMessage(1L);
        when(outboxRepository.updateStatusById(1L, OutboxStatus.PENDING, OutboxStatus.SENDING)).thenReturn(1);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(message));
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(message.getTopic(), message.getAggregateId(), message.getPayload()))
                .thenReturn(failedFuture);
        when(outboxRepository.updateStatusById(1L, OutboxStatus.SENDING, OutboxStatus.PENDING)).thenReturn(1);

        immediatePublisher.handleOutboxSaved(new OutboxSavedEvent(1L));

        verify(outboxRepository).updateStatusById(1L, OutboxStatus.SENDING, OutboxStatus.PENDING);
    }

    private OutboxMessage createMessage(Long id) {
        OutboxMessage message = OutboxMessage.create(
                "evt-1",
                "ITEM",
                "1",
                "item-events",
                "ITEM_CREATED",
                "{\"eventId\":\"evt-1\"}"
        );
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
