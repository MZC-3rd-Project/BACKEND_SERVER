package com.example.review.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.review.client.OrderReviewEligibilityClient;
import com.example.review.domain.Review;
import com.example.review.domain.ReviewRepository;
import com.example.review.dto.request.CreateReviewRequest;
import com.example.review.dto.response.ReviewResponse;
import com.example.review.event.ReviewCreatedEvent;
import com.example.review.exception.ReviewErrorCode;
import com.example.review.service.ReviewMediaLinkSyncService;
import com.example.review.service.ReviewMediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderReviewEligibilityClient orderReviewEligibilityClient;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ReviewMediaService reviewMediaService;

    @Mock
    private ReviewMediaLinkSyncService reviewMediaLinkSyncService;

    @InjectMocks
    private ReviewCommandService reviewCommandService;

    @Test
    void createReview_savesReviewAndSyncsMetrics() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setOrderId(1L);
        request.setItemId(2L);
        request.setRating(5);
        request.setTitle("좋아요");
        request.setContent("재구매 의사 있습니다.");
        request.setImageMediaIds(List.of(11L, 12L));

        when(orderReviewEligibilityClient.isEligible(1L, 100L, 2L)).thenReturn(true);
        when(reviewRepository.existsByOrderIdAndItemIdAndUserIdAndDeletedAtIsNull(1L, 2L, 100L)).thenReturn(false);
        when(reviewMediaService.validateMediaIds(List.of(11L, 12L))).thenReturn(List.of(11L, 12L));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 50L);
            return saved;
        });
        when(reviewRepository.aggregateByItemId(2L)).thenReturn(new ReviewRepository.ReviewAggregate() {
            @Override
            public Long getReviewCount() {
                return 1L;
            }

            @Override
            public Double getAverageRating() {
                return 5.0d;
            }
        });
        when(reviewMediaService.resolveMediaUrls(List.of(11L, 12L)))
                .thenReturn(Map.of(11L, "https://cdn/review-11.jpg", 12L, "https://cdn/review-12.jpg"));

        ReviewResponse response = reviewCommandService.createReview(request, 100L);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getImages()).hasSize(2);
        verify(reviewMediaLinkSyncService).syncAfterCommit(50L, List.of(11L, 12L));
        verify(eventPublisher).publish(any(ReviewCreatedEvent.class), any());
    }

    @Test
    void createReview_throwsWhenOrderNotEligible() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setOrderId(1L);
        request.setItemId(2L);
        request.setRating(5);
        request.setTitle("좋아요");
        request.setContent("재구매 의사 있습니다.");

        when(orderReviewEligibilityClient.isEligible(1L, 100L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> reviewCommandService.createReview(request, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
    }

    @Test
    void createReview_throwsWhenDuplicateReviewDetectedOnFlush() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setOrderId(1L);
        request.setItemId(2L);
        request.setRating(5);
        request.setTitle("좋아요");
        request.setContent("재구매 의사 있습니다.");

        when(orderReviewEligibilityClient.isEligible(1L, 100L, 2L)).thenReturn(true);
        when(reviewRepository.existsByOrderIdAndItemIdAndUserIdAndDeletedAtIsNull(1L, 2L, 100L)).thenReturn(false);
        when(reviewMediaService.validateMediaIds(null)).thenReturn(List.of());
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> reviewCommandService.createReview(request, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
    }
}
