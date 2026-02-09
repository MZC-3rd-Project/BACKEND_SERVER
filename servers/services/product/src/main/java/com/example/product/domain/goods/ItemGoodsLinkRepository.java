package com.example.product.domain.goods;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemGoodsLinkRepository extends JpaRepository<ItemGoodsLink, Long> {

    List<ItemGoodsLink> findByPerformanceItemId(Long performanceItemId);

    List<ItemGoodsLink> findByGoodsItemId(Long goodsItemId);

    void deleteAllByGoodsItemId(Long goodsItemId);
}
