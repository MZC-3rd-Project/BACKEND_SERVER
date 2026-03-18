package com.example.orderquery.repository;

import com.example.orderquery.entity.Orders;
import com.example.orderquery.entity.Enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Long> {

    List<Orders> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Orders> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

    Optional<Orders> findByIdAndUserId(Long id, Long userId);
}
