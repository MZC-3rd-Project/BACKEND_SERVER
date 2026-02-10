package com.example.product.repository;

import com.example.product.entity.goods.ItemGoodsLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemGoodsLinkRepository extends JpaRepository<ItemGoodsLink, Long> {

    List<ItemGoodsLink> findByPerformanceItemId(Long performanceItemId);

    List<ItemGoodsLink> findByGoodsItemId(Long goodsItemId);

    List<ItemGoodsLink> findByGoodsItemIdIn(List<Long> goodsItemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ItemGoodsLink l SET l.deletedAt = CURRENT_TIMESTAMP WHERE l.goodsItemId = :goodsItemId AND l.deletedAt IS NULL")
    void softDeleteAllByGoodsItemId(@Param("goodsItemId") Long goodsItemId);
}
