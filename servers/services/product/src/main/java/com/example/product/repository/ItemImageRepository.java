package com.example.product.repository;

import com.example.product.entity.image.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    @Query("SELECT i FROM ItemImage i WHERE i.itemId = :itemId ORDER BY i.sortOrder ASC, i.id ASC")
    List<ItemImage> findByItemIdOrderBySortOrder(@Param("itemId") Long itemId);

    List<ItemImage> findByItemIdInOrderByItemIdAscSortOrderAsc(List<Long> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ItemImage i SET i.deletedAt = CURRENT_TIMESTAMP WHERE i.itemId = :itemId AND i.deletedAt IS NULL")
    void softDeleteAllByItemId(@Param("itemId") Long itemId);
}
