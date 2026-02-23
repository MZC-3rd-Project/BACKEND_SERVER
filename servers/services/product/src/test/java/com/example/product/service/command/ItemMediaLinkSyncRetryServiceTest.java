package com.example.product.service.command;

import com.example.product.entity.image.ItemMediaLinkSyncStatus;
import com.example.product.entity.image.ItemMediaLinkSyncTask;
import com.example.product.repository.ItemMediaLinkSyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemMediaLinkSyncRetryServiceTest {

    @Mock
    private ItemMediaLinkSyncTaskRepository retryRepository;
    @Mock
    private MediaReferenceService mediaReferenceService;

    private ItemMediaLinkSyncRetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new ItemMediaLinkSyncRetryService(
                retryRepository,
                mediaReferenceService,
                new TransactionTemplate(new NoOpTransactionManager())
        );
        ReflectionTestUtils.setField(retryService, "processingStaleThresholdSeconds", 120L);
        ReflectionTestUtils.setField(retryService, "maxRetryCount", 5);
        ReflectionTestUtils.setField(retryService, "baseDelaySeconds", 30L);
        ReflectionTestUtils.setField(retryService, "maxDelaySeconds", 600L);
    }

    @Test
    void enqueue_whenTaskExists_updatesPayloadAndResetsToPending() {
        Long itemId = 1L;
        ItemMediaLinkSyncTask task = createTask(101L, itemId, 10L, List.of(20L));
        task.markCompleted(10L, List.of(20L));

        when(retryRepository.findByItemIdForUpdate(itemId)).thenReturn(Optional.of(task));

        retryService.enqueue(itemId, 11L, List.of(21L, 22L), "sync failed");

        assertThat(task.getStatus()).isEqualTo(ItemMediaLinkSyncStatus.PENDING);
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getThumbnailMediaId()).isEqualTo(11L);
        assertThat(task.getGalleryMediaIdList()).containsExactly(21L, 22L);
    }

    @Test
    void processDueRetries_whenSyncSucceeds_marksTaskCompleted() {
        Long taskId = 101L;
        Long itemId = 1L;
        ItemMediaLinkSyncTask task = createTask(taskId, itemId, 10L, List.of(20L, 21L));

        when(retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(ItemMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(ItemMediaLinkSyncStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(task));
        when(retryRepository.claimForProcessing(
                eq(taskId),
                eq(ItemMediaLinkSyncStatus.PENDING),
                eq(ItemMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            task.markProcessing();
            return 1;
        });
        when(retryRepository.findById(taskId)).thenReturn(Optional.of(task));

        retryService.processDueRetries();

        verify(mediaReferenceService).syncItemMediaLinks(itemId, 10L, List.of(20L, 21L));
        assertThat(task.getStatus()).isEqualTo(ItemMediaLinkSyncStatus.COMPLETED);
        assertThat(task.getRetryCount()).isZero();
    }

    @Test
    void processDueRetries_whenSyncFails_schedulesNextRetry() {
        Long taskId = 102L;
        Long itemId = 2L;
        ItemMediaLinkSyncTask task = createTask(taskId, itemId, 30L, List.of(40L));

        when(retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(ItemMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(ItemMediaLinkSyncStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(task));
        when(retryRepository.claimForProcessing(
                eq(taskId),
                eq(ItemMediaLinkSyncStatus.PENDING),
                eq(ItemMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            task.markProcessing();
            return 1;
        });
        when(retryRepository.findById(taskId)).thenReturn(Optional.of(task));
        doThrow(new RuntimeException("media unavailable"))
                .when(mediaReferenceService)
                .syncItemMediaLinks(itemId, 30L, List.of(40L));

        retryService.processDueRetries();

        assertThat(task.getStatus()).isEqualTo(ItemMediaLinkSyncStatus.PENDING);
        assertThat(task.getRetryCount()).isEqualTo(1);
    }

    @Test
    void processDueRetries_whenTaskClaimFails_skipsProcessing() {
        Long taskId = 103L;
        ItemMediaLinkSyncTask task = createTask(taskId, 3L, 50L, List.of(60L));

        when(retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(ItemMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(ItemMediaLinkSyncStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(task));
        when(retryRepository.claimForProcessing(
                eq(taskId),
                eq(ItemMediaLinkSyncStatus.PENDING),
                eq(ItemMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(0);

        retryService.processDueRetries();

        verify(mediaReferenceService, never()).syncItemMediaLinks(3L, 50L, List.of(60L));
    }

    private ItemMediaLinkSyncTask createTask(Long id, Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        ItemMediaLinkSyncTask task = ItemMediaLinkSyncTask.create(itemId, thumbnailMediaId, galleryMediaIds, "initial");
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
