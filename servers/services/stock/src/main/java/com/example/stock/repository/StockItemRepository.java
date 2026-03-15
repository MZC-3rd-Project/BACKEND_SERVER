package com.example.stock.repository;

import com.example.stock.entity.StockItem;
import com.example.stock.entity.StockItemType;
import com.example.stock.service.query.view.StockItemQueryView;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    @Query("""
            SELECT new com.example.stock.service.query.view.StockItemQueryView(
                s.id,
                s.itemId,
                s.stockItemType,
                s.referenceId,
                s.totalQuantity,
                s.availableQuantity,
                s.reservedQuantity
            )
            FROM StockItem s
            WHERE s.id = :id
            """)
    Optional<StockItemQueryView> findViewById(@Param("id") Long id);

    Optional<StockItem> findByItemIdAndStockItemTypeAndReferenceId(Long itemId, StockItemType stockItemType, Long referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM StockItem s
            WHERE s.itemId = :itemId
              AND s.stockItemType = :stockItemType
              AND s.referenceId = :referenceId
            """)
    Optional<StockItem> findByItemIdAndStockItemTypeAndReferenceIdWithLock(
            @Param("itemId") Long itemId,
            @Param("stockItemType") StockItemType stockItemType,
            @Param("referenceId") Long referenceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id = :id")
    Optional<StockItem> findByIdWithLock(@Param("id") Long id);

    List<StockItem> findByItemId(Long itemId);

    @Query("""
            SELECT new com.example.stock.service.query.view.StockItemQueryView(
                s.id,
                s.itemId,
                s.stockItemType,
                s.referenceId,
                s.totalQuantity,
                s.availableQuantity,
                s.reservedQuantity
            )
            FROM StockItem s
            WHERE s.itemId = :itemId
            ORDER BY s.id ASC
            """)
    List<StockItemQueryView> findViewsByItemId(@Param("itemId") Long itemId);

    List<StockItem> findByItemIdAndStockItemType(Long itemId, StockItemType stockItemType);

    @Query("SELECT COALESCE(SUM(s.availableQuantity), 0) FROM StockItem s WHERE s.itemId = :itemId")
    Long sumAvailableQuantityByItemId(@Param("itemId") Long itemId);
}
