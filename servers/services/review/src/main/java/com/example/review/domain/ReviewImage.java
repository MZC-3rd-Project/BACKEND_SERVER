package com.example.review.domain;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@Table(
        name = "review_images",
        indexes = {
                @Index(name = "idx_review_images_review_id", columnList = "review_id")
        }
)
public class ReviewImage extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static ReviewImage create(Long mediaId, Integer sortOrder) {
        ReviewImage reviewImage = new ReviewImage();
        reviewImage.mediaId = mediaId;
        reviewImage.sortOrder = sortOrder;
        return reviewImage;
    }

    void assignReview(Review review) {
        this.review = review;
    }
}
