package com.example.stock.repository;

import com.example.stock.entity.StockHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    Page<StockHistory> findByStockItemIdOrderByCreatedAtDesc(Long stockItemId, Pageable pageable);
}
