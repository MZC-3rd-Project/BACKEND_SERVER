package com.example.stock.repository;

import com.example.stock.entity.StockHistory;
import com.example.stock.service.query.view.StockHistoryQueryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    Page<StockHistory> findByStockItemIdOrderByCreatedAtDesc(Long stockItemId, Pageable pageable);

    @Query("""
            SELECT new com.example.stock.service.query.view.StockHistoryQueryView(
                h.id,
                h.stockItemId,
                h.changeType,
                h.quantity,
                h.reason,
                h.reservationId,
                h.createdAt
            )
            FROM StockHistory h
            WHERE h.stockItemId = :stockItemId
            ORDER BY h.createdAt DESC
            """)
    Page<StockHistoryQueryView> findViewsByStockItemIdOrderByCreatedAtDesc(
            @Param("stockItemId") Long stockItemId,
            Pageable pageable
    );
}
