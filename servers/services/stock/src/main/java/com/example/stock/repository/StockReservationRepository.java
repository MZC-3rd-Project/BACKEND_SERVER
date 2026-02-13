package com.example.stock.repository;

import com.example.stock.entity.ReservationStatus;
import com.example.stock.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @Query("SELECT r FROM StockReservation r WHERE r.status = :status AND r.expiredAt < :now")
    List<StockReservation> findExpiredReservations(@Param("status") ReservationStatus status, @Param("now") LocalDateTime now);

    @Query("""
            SELECT r.id
            FROM StockReservation r
            WHERE r.status = :status
              AND r.expiredAt < :now
              AND (:cursor IS NULL OR r.id < :cursor)
            ORDER BY r.id DESC
            """)
    List<Long> findExpiredReservationIdsWithCursor(@Param("status") ReservationStatus status,
                                                    @Param("now") LocalDateTime now,
                                                    @Param("cursor") Long cursor,
                                                    org.springframework.data.domain.Pageable pageable);

    List<StockReservation> findByStockItemIdAndStatus(Long stockItemId, ReservationStatus status);

    List<StockReservation> findByOrderId(Long orderId);
}
