package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalyticsRawSalesEventRepository extends JpaRepository<AnalyticsRawSalesEvent, Long> {

    Optional<AnalyticsRawSalesEvent> findByEventId(String eventId);

    Optional<AnalyticsRawSalesEvent> findTopByPurchaseIdOrderByOccurredAtDesc(Long purchaseId);

    List<AnalyticsRawSalesEvent> findByStoreIdAndOccurredAtBetween(
            Long storeId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<AnalyticsRawSalesEvent> findBySellerIdAndOccurredAtBetween(
            Long sellerId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<AnalyticsRawSalesEvent> findByStoreIdAndSellerIdAndOccurredAtBetween(
            Long storeId,
            Long sellerId,
            LocalDateTime from,
            LocalDateTime to
    );
}
