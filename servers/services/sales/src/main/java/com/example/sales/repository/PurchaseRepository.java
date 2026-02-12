package com.example.sales.repository;

import com.example.sales.entity.Purchase;
import com.example.sales.entity.PurchaseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByOrderId(Long orderId);

    @Query("SELECT p FROM Purchase p WHERE p.userId = :userId AND p.status = :status " +
            "AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    List<Purchase> findByUserIdAndStatusWithCursor(@Param("userId") Long userId,
                                                    @Param("status") PurchaseStatus status,
                                                    @Param("cursor") Long cursor,
                                                    Pageable pageable);

    @Query("SELECT p FROM Purchase p WHERE p.userId = :userId " +
            "AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    List<Purchase> findByUserIdWithCursor(@Param("userId") Long userId,
                                          @Param("cursor") Long cursor,
                                          Pageable pageable);

    Optional<Purchase> findByReservationId(Long reservationId);
}
