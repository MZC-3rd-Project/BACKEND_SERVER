package com.example.orderquery.repository;

import com.example.orderquery.entity.Enums.OrderSourceType;
import com.example.orderquery.entity.Enums.OrderStatus;
import com.example.orderquery.entity.Orders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Long> {

    Page<Orders> findAllByUser_Id(Long userId, Pageable pageable);

    Page<Orders> findAllByUser_IdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Page<Orders> findAllByUser_IdAndSourceType(Long userId, OrderSourceType sourceType, Pageable pageable);

    Optional<Orders> findByIdAndUser_Id(Long orderId, Long userId);
}
