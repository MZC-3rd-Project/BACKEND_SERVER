package com.example.order.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByPurchaseId(Long purchaseId);

    @Query("""
            SELECT o FROM Order o
            WHERE o.userId = :userId
              AND (:cursorId IS NULL OR o.id < :cursorId)
            ORDER BY o.id DESC
            """)
    List<Order> findByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    long countByUserId(Long userId);
}
