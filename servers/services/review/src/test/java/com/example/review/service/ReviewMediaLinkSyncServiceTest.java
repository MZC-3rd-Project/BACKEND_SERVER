package com.example.review.service;

import com.example.core.exception.BusinessException;
import com.example.review.exception.ReviewErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReviewMediaLinkSyncServiceTest {

    @Mock
    private ReviewMediaService reviewMediaService;

    @Mock
    private ReviewMediaLinkSyncRetryService retryService;

    @InjectMocks
    private ReviewMediaLinkSyncService reviewMediaLinkSyncService;

    @Test
    void syncAfterCommit_whenSyncFails_shouldEnqueueRetryAndNotThrow() {
        Long reviewId = 1L;
        doThrow(new BusinessException(ReviewErrorCode.MEDIA_SERVICE_ERROR))
                .when(reviewMediaService)
                .syncReviewImages(reviewId, List.of(20L, 21L));

        assertThatCode(() -> reviewMediaLinkSyncService.syncAfterCommit(reviewId, List.of(20L, 21L)))
                .doesNotThrowAnyException();

        verify(retryService).enqueue(reviewId, List.of(20L, 21L), ReviewErrorCode.MEDIA_SERVICE_ERROR.getMessage());
    }

    @Test
    void syncAfterCommit_withoutMediaIds_skipsSync() {
        reviewMediaLinkSyncService.syncAfterCommit(2L, List.of());

        verifyNoInteractions(reviewMediaService);
        verifyNoInteractions(retryService);
    }
}
