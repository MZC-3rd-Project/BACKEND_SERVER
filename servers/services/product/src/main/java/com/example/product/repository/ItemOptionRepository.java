package com.example.product.repository;

import com.example.product.entity.goods.ItemOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemOptionRepository extends JpaRepository<ItemOption, Long> {

    List<ItemOption> findByItemId(Long itemId);

    List<ItemOption> findByItemIdIn(List<Long> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ItemOption o SET o.deletedAt = CURRENT_TIMESTAMP WHERE o.itemId = :itemId AND o.deletedAt IS NULL")
    void softDeleteAllByItemId(@Param("itemId") Long itemId);
}
