package com.example.product.domain.goods;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShippingInfoRepository extends JpaRepository<ShippingInfo, Long> {

    Optional<ShippingInfo> findByItemId(Long itemId);
}
