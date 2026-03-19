package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsJourneyEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsJourneyEventRepository extends JpaRepository<AnalyticsJourneyEvent, Long> {

    List<AnalyticsJourneyEvent> findByStoreIdAndOccurredAtBetween(
            Long storeId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<AnalyticsJourneyEvent> findBySellerIdAndOccurredAtBetween(
            Long sellerId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<AnalyticsJourneyEvent> findByStoreIdAndSellerIdAndOccurredAtBetween(
            Long storeId,
            Long sellerId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<AnalyticsJourneyEvent> findByOrderIdAndEventTypeOrderByOccurredAtAsc(Long orderId, String eventType);
}
