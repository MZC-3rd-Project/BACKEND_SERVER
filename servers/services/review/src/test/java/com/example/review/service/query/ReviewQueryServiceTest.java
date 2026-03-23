package com.example.review.service.query;

import com.example.review.domain.Review;
import com.example.review.domain.ReviewImage;
import com.example.review.domain.ReviewRepository;
import com.example.review.dto.response.ReviewResponse;
import com.example.review.service.ReviewMediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewMediaService reviewMediaService;

    @InjectMocks
    private ReviewQueryService reviewQueryService;

    @Test
    void getReviewsByItem_returnsSortedImagesWithResolvedUrls() {
        Review review = Review.create(1L, 2L, 3L, 5, "좋아요", "만족합니다.");
        ReflectionTestUtils.setField(review, "id", 100L);
        review.addImage(ReviewImage.create(12L, 1));
        review.addImage(ReviewImage.create(11L, 0));

        when(reviewRepository.findByItemIdAndDeletedAtIsNullOrderByCreatedAtDesc(2L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(reviewMediaService.resolveMediaUrls(List.of(12L, 11L)))
                .thenReturn(Map.of(11L, "https://cdn/review-11.jpg", 12L, "https://cdn/review-12.jpg"));

        ReviewResponse response = reviewQueryService.getReviewsByItem(2L, PageRequest.of(0, 20))
                .getContent()
                .getFirst();

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getImages()).hasSize(2);
        assertThat(response.getImages().get(0).getMediaId()).isEqualTo(11L);
        assertThat(response.getImages().get(0).getMediaUrl()).isEqualTo("https://cdn/review-11.jpg");
        assertThat(response.getImages().get(1).getMediaId()).isEqualTo(12L);
        assertThat(response.getImages().get(1).getMediaUrl()).isEqualTo("https://cdn/review-12.jpg");
    }
}
