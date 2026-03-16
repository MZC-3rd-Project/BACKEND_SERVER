package com.example.orderquery.repository;

import com.example.orderquery.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder_Id(Long orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.item WHERE oi.order.id = :orderId")
    List<OrderItem> findAllWithItemByOrderId(@Param("orderId") Long orderId);
}
