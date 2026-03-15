package com.example.stock.repository;

import com.example.stock.entity.ReservationStatus;
import com.example.stock.entity.StockReservation;
import com.example.stock.service.query.view.StockReservationQueryView;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM StockReservation r WHERE r.id = :id")
    java.util.Optional<StockReservation> findByIdWithLock(@Param("id") Long id);

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

    @Query("""
            SELECT new com.example.stock.service.query.view.StockReservationQueryView(
                r.stockItemId,
                r.userId,
                r.orderId,
                r.quantity,
                r.status,
                r.expiredAt
            )
            FROM StockReservation r
            WHERE r.orderId = :orderId
            ORDER BY r.id ASC
            """)
    List<StockReservationQueryView> findViewsByOrderId(@Param("orderId") Long orderId);
}
