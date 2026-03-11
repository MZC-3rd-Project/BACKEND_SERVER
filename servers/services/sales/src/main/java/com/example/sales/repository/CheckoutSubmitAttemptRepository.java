package com.example.sales.repository;

import com.example.sales.entity.CheckoutSubmitAttempt;
import com.example.sales.entity.CheckoutSubmitAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutSubmitAttemptRepository extends JpaRepository<CheckoutSubmitAttempt, Long> {

    Optional<CheckoutSubmitAttempt> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<CheckoutSubmitAttempt> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            CheckoutSubmitAttemptStatus status,
            LocalDateTime nextRetryAt
    );
}
