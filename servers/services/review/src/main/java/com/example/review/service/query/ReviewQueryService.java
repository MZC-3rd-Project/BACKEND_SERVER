package com.example.review.service.query;

import com.example.review.domain.Review;
import com.example.review.domain.ReviewRepository;
import com.example.review.dto.response.ReviewResponse;
import com.example.review.service.ReviewMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;
    private final ReviewMediaService reviewMediaService;

    public Page<ReviewResponse> getReviewsByItem(Long itemId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByItemIdAndDeletedAtIsNullOrderByCreatedAtDesc(itemId, pageable);

        List<Long> mediaIds = page.getContent().stream()
                .flatMap(review -> review.getImages().stream())
                .map(image -> image.getMediaId())
                .distinct()
                .toList();
        Map<Long, String> mediaUrlMap = resolveMediaUrlMap(mediaIds);

        return page.map(review -> ReviewResponse.from(review, mediaUrlMap));
    }

    private Map<Long, String> resolveMediaUrlMap(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Map.of();
        }

        try {
            return reviewMediaService.resolveMediaUrls(mediaIds);
        } catch (RuntimeException exception) {
            log.warn("Review media url resolution failed. Falling back to review payload without urls. mediaCount={}",
                    mediaIds.size(), exception);
            return Map.of();
        }
    }
}
