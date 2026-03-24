package com.example.review.domain;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@Table(
        name = "reviews",
        indexes = {
                @Index(name = "idx_reviews_item_id", columnList = "item_id"),
                @Index(name = "idx_reviews_user_id", columnList = "user_id")
        }
)
public class Review extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReviewImage> images = new ArrayList<>();

    public static Review create(Long orderId, Long itemId, Long userId, Integer rating, String title, String content) {
        Review review = new Review();
        review.orderId = orderId;
        review.itemId = itemId;
        review.userId = userId;
        review.rating = rating;
        review.title = title;
        review.content = content;
        return review;
    }

    public void addImage(ReviewImage image) {
        this.images.add(image);
        image.assignReview(this);
    }
}
