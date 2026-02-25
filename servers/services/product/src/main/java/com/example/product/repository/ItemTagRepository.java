package com.example.product.repository;

import com.example.product.entity.item.ItemTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemTagRepository extends JpaRepository<ItemTag, Long> {

    List<ItemTag> findByItemIdOrderBySortOrderAsc(Long itemId);

    List<ItemTag> findByItemIdInOrderByItemIdAscSortOrderAsc(List<Long> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ItemTag t SET t.deletedAt = CURRENT_TIMESTAMP WHERE t.itemId = :itemId AND t.deletedAt IS NULL")
    void softDeleteAllByItemId(@Param("itemId") Long itemId);
}
