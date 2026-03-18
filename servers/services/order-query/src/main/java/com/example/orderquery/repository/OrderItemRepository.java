package com.example.orderquery.repository;

import com.example.orderquery.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByItemId(Long itemId);

    List<OrderItem> findByStoreId(Long storeId);
}
