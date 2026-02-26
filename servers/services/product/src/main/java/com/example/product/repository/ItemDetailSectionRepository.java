package com.example.product.repository;

import com.example.product.entity.item.ItemDetailSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemDetailSectionRepository extends JpaRepository<ItemDetailSection, Long> {

    List<ItemDetailSection> findByItemIdOrderBySortOrderAsc(Long itemId);

    List<ItemDetailSection> findByItemIdInOrderByItemIdAscSortOrderAsc(List<Long> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ItemDetailSection s SET s.deletedAt = CURRENT_TIMESTAMP WHERE s.itemId = :itemId AND s.deletedAt IS NULL")
    void softDeleteAllByItemId(@Param("itemId") Long itemId);
}
