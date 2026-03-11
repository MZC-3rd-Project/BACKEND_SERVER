package com.example.stock.repository;

import com.example.stock.entity.OrderReserveIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderReserveIdempotencyRepository extends JpaRepository<OrderReserveIdempotency, Long> {

    Optional<OrderReserveIdempotency> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
