package com.example.product.repository;

import com.example.product.entity.item.ItemFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemFeatureRepository extends JpaRepository<ItemFeature, Long> {

    List<ItemFeature> findByItemIdOrderBySortOrderAsc(Long itemId);

    List<ItemFeature> findByItemIdInOrderByItemIdAscSortOrderAsc(List<Long> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ItemFeature f SET f.deletedAt = CURRENT_TIMESTAMP WHERE f.itemId = :itemId AND f.deletedAt IS NULL")
    void softDeleteAllByItemId(@Param("itemId") Long itemId);
}
