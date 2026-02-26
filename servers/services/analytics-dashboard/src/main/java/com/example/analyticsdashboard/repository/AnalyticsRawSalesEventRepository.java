package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalyticsRawSalesEventRepository extends JpaRepository<AnalyticsRawSalesEvent, Long> {

    Optional<AnalyticsRawSalesEvent> findByEventId(String eventId);

    List<AnalyticsRawSalesEvent> findByStoreIdAndOccurredAtBetween(
            Long storeId,
            LocalDateTime from,
            LocalDateTime to
    );
}
