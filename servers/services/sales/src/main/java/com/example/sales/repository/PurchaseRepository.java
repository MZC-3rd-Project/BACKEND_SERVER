package com.example.sales.repository;

import com.example.sales.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByOrderId(Long orderId);

    Optional<Purchase> findByReservationId(Long reservationId);
}
