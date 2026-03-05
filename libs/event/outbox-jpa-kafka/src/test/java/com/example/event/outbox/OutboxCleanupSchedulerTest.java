package com.example.event.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupSchedulerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Test
    void cleanupPublishedMessages_deletesInBatchesUntilTailBatch() {
        OutboxProperties properties = new OutboxProperties();
        properties.getCleanup().setRetentionDays(7);
        properties.getCleanup().setBatchSize(3);

        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(outboxRepository, properties);

        when(outboxRepository.deleteTopByStatusAndCreatedBefore(eq("PUBLISHED"), any(LocalDateTime.class), eq(3)))
                .thenReturn(3, 1);
        when(outboxRepository.deleteTopByStatusAndCreatedBefore(eq("FAILED"), any(LocalDateTime.class), eq(3)))
                .thenReturn(2);

        scheduler.cleanupPublishedMessages();

        verify(outboxRepository, times(2))
                .deleteTopByStatusAndCreatedBefore(eq("PUBLISHED"), any(LocalDateTime.class), eq(3));
        verify(outboxRepository, times(1))
                .deleteTopByStatusAndCreatedBefore(eq("FAILED"), any(LocalDateTime.class), eq(3));
    }
}
