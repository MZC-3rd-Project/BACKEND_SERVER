package com.example.review.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.review.domain.ReviewImage;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReviewImageResponse {

    @SnowflakeId
    private Long mediaId;
    private String mediaUrl;
    private Integer sortOrder;

    public static ReviewImageResponse from(ReviewImage image, String mediaUrl) {
        return ReviewImageResponse.builder()
                .mediaId(image.getMediaId())
                .mediaUrl(mediaUrl)
                .sortOrder(image.getSortOrder())
                .build();
    }
}
