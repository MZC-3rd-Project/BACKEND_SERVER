package com.example.review.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderIdAndItemIdAndUserIdAndDeletedAtIsNull(Long orderId, Long itemId, Long userId);

    @EntityGraph(attributePaths = "images")
    Page<Review> findByItemIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long itemId, Pageable pageable);

    @Query("""
            select count(r) as reviewCount, avg(r.rating) as averageRating
            from Review r
            where r.itemId = :itemId
              and r.deletedAt is null
            """)
    ReviewAggregate aggregateByItemId(@Param("itemId") Long itemId);

    interface ReviewAggregate {
        Long getReviewCount();
        Double getAverageRating();
    }
}
