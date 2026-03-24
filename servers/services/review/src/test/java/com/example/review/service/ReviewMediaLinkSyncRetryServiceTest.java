package com.example.review.service;

import com.example.review.entity.media.ReviewMediaLinkSyncStatus;
import com.example.review.entity.media.ReviewMediaLinkSyncTask;
import com.example.review.repository.ReviewMediaLinkSyncTaskRepository;
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
class ReviewMediaLinkSyncRetryServiceTest {

    @Mock
    private ReviewMediaLinkSyncTaskRepository retryRepository;

    @Mock
    private ReviewMediaService reviewMediaService;

    private ReviewMediaLinkSyncRetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new ReviewMediaLinkSyncRetryService(
                retryRepository,
                reviewMediaService,
                new TransactionTemplate(new NoOpTransactionManager())
        );
        ReflectionTestUtils.setField(retryService, "processingStaleThresholdSeconds", 120L);
        ReflectionTestUtils.setField(retryService, "maxRetryCount", 5);
        ReflectionTestUtils.setField(retryService, "baseDelaySeconds", 30L);
        ReflectionTestUtils.setField(retryService, "maxDelaySeconds", 600L);
    }

    @Test
    void enqueue_whenTaskExists_updatesPayloadAndResetsToPending() {
        Long reviewId = 1L;
        ReviewMediaLinkSyncTask task = createTask(101L, reviewId, List.of(20L));
        task.markCompleted(List.of(20L));

        when(retryRepository.findByReviewIdForUpdate(reviewId)).thenReturn(Optional.of(task));

        retryService.enqueue(reviewId, List.of(21L, 22L), "sync failed");

        assertThat(task.getStatus()).isEqualTo(ReviewMediaLinkSyncStatus.PENDING);
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getGalleryMediaIdList()).containsExactly(21L, 22L);
    }

    @Test
    void processDueRetries_whenSyncSucceeds_marksTaskCompleted() {
        Long taskId = 101L;
        Long reviewId = 1L;
        ReviewMediaLinkSyncTask task = createTask(taskId, reviewId, List.of(20L, 21L));

        when(retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(ReviewMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(ReviewMediaLinkSyncStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(task));
        when(retryRepository.claimForProcessing(
                eq(taskId),
                eq(ReviewMediaLinkSyncStatus.PENDING),
                eq(ReviewMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            task.markProcessing();
            return 1;
        });
        when(retryRepository.findById(taskId)).thenReturn(Optional.of(task));

        retryService.processDueRetries();

        verify(reviewMediaService).syncReviewImages(reviewId, List.of(20L, 21L));
        assertThat(task.getStatus()).isEqualTo(ReviewMediaLinkSyncStatus.COMPLETED);
        assertThat(task.getRetryCount()).isZero();
    }

    @Test
    void processDueRetries_whenSyncFails_schedulesNextRetry() {
        Long taskId = 102L;
        Long reviewId = 2L;
        ReviewMediaLinkSyncTask task = createTask(taskId, reviewId, List.of(40L));

        when(retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(ReviewMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(ReviewMediaLinkSyncStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(task));
        when(retryRepository.claimForProcessing(
                eq(taskId),
                eq(ReviewMediaLinkSyncStatus.PENDING),
                eq(ReviewMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            task.markProcessing();
            return 1;
        });
        when(retryRepository.findById(taskId)).thenReturn(Optional.of(task));
        doThrow(new RuntimeException("media unavailable"))
                .when(reviewMediaService)
                .syncReviewImages(reviewId, List.of(40L));

        retryService.processDueRetries();

        assertThat(task.getStatus()).isEqualTo(ReviewMediaLinkSyncStatus.PENDING);
        assertThat(task.getRetryCount()).isEqualTo(1);
    }

    @Test
    void processDueRetries_whenTaskClaimFails_skipsProcessing() {
        Long taskId = 103L;
        ReviewMediaLinkSyncTask task = createTask(taskId, 3L, List.of(60L));

        when(retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(ReviewMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(ReviewMediaLinkSyncStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(task));
        when(retryRepository.claimForProcessing(
                eq(taskId),
                eq(ReviewMediaLinkSyncStatus.PENDING),
                eq(ReviewMediaLinkSyncStatus.PROCESSING),
                any(LocalDateTime.class)
        )).thenReturn(0);

        retryService.processDueRetries();

        verify(reviewMediaService, never()).syncReviewImages(3L, List.of(60L));
    }

    private ReviewMediaLinkSyncTask createTask(Long id, Long reviewId, List<Long> galleryMediaIds) {
        ReviewMediaLinkSyncTask task = ReviewMediaLinkSyncTask.create(reviewId, galleryMediaIds, "initial");
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
