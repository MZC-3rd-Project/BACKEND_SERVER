package com.example.review.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.review.client.OrderReviewEligibilityClient;
import com.example.review.domain.Review;
import com.example.review.domain.ReviewImage;
import com.example.review.domain.ReviewRepository;
import com.example.review.dto.request.CreateReviewRequest;
import com.example.review.dto.response.ReviewResponse;
import com.example.review.event.ReviewCreatedEvent;
import com.example.review.exception.ReviewErrorCode;
import com.example.review.service.ReviewMediaService;
import com.example.review.service.ReviewMediaLinkSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewCommandService {

    private final ReviewRepository reviewRepository;
    private final OrderReviewEligibilityClient orderReviewEligibilityClient;
    private final ReviewMediaService reviewMediaService;
    private final ReviewMediaLinkSyncService reviewMediaLinkSyncService;
    private final EventPublisher eventPublisher;

    public ReviewResponse createReview(CreateReviewRequest request, Long userId) {
        if (!orderReviewEligibilityClient.isEligible(request.getOrderId(), userId, request.getItemId())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
        }
        if (reviewRepository.existsByOrderIdAndItemIdAndUserIdAndDeletedAtIsNull(
                request.getOrderId(),
                request.getItemId(),
                userId
        )) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }

        List<Long> mediaIds = reviewMediaService.validateMediaIds(request.getImageMediaIds());

        Review review = Review.create(
                request.getOrderId(),
                request.getItemId(),
                userId,
                request.getRating(),
                request.getTitle(),
                request.getContent()
        );
        for (int index = 0; index < mediaIds.size(); index++) {
            review.addImage(ReviewImage.create(mediaIds.get(index), index));
        }

        Review saved;
        try {
            saved = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS, e);
        }
        reviewMediaLinkSyncService.syncAfterCommit(saved.getId(), mediaIds);

        ReviewRepository.ReviewAggregate aggregate = reviewRepository.aggregateByItemId(saved.getItemId());
        BigDecimal averageRating = scaleAverage(aggregate.getAverageRating());
        Long reviewCount = aggregate.getReviewCount() == null ? 0L : aggregate.getReviewCount();

        eventPublisher.publish(
                new ReviewCreatedEvent(
                        saved.getId(),
                        saved.getOrderId(),
                        saved.getItemId(),
                        saved.getUserId(),
                        saved.getRating(),
                        averageRating,
                        reviewCount
                ),
                EventMetadata.of("Review", String.valueOf(saved.getId()))
        );

        Map<Long, String> mediaUrlMap = reviewMediaService.resolveMediaUrls(mediaIds);
        return ReviewResponse.from(saved, mediaUrlMap);
    }

    private BigDecimal scaleAverage(Double averageRating) {
        if (averageRating == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(averageRating).setScale(2, RoundingMode.HALF_UP);
    }
}
