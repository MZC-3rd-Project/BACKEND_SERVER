package com.example.product.repository;

import com.example.product.entity.goods.ShippingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShippingInfoRepository extends JpaRepository<ShippingInfo, Long> {

    Optional<ShippingInfo> findByItemId(Long itemId);

    List<ShippingInfo> findByItemIdIn(List<Long> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ShippingInfo s SET s.deletedAt = CURRENT_TIMESTAMP WHERE s.itemId = :itemId AND s.deletedAt IS NULL")
    void softDeleteByItemId(@Param("itemId") Long itemId);
}
