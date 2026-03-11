package com.example.sales.repository;

import com.example.sales.entity.CheckoutSession;
import com.example.sales.entity.CheckoutSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {

    Optional<CheckoutSession> findByOrderId(Long orderId);

    Optional<CheckoutSession> findTopByUserIdAndIdempotencyKeyOrderByCreatedAtDesc(Long userId, String idempotencyKey);

    List<CheckoutSession> findTop100ByStatusInAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            List<CheckoutSessionStatus> statuses,
            LocalDateTime expiresAt
    );
}
