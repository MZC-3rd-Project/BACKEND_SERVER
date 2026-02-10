package com.example.stock.repository;

import com.example.stock.entity.StockItem;
import com.example.stock.entity.StockItemType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findByItemIdAndStockItemTypeAndReferenceId(Long itemId, StockItemType stockItemType, Long referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id = :id")
    Optional<StockItem> findByIdWithLock(@Param("id") Long id);

    List<StockItem> findByItemId(Long itemId);

    List<StockItem> findByItemIdAndStockItemType(Long itemId, StockItemType stockItemType);
}
