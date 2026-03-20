package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsRawSalesEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsRawSalesEventAggregateRow(
                upper(e.eventType),
                count(e),
                coalesce(sum(e.grossAmount), 0L),
                coalesce(sum(e.netAmount), 0L)
            )
            from AnalyticsRawSalesEvent e
            where e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            group by upper(e.eventType)
            """)
    List<AnalyticsRawSalesEventAggregateRow> aggregateBySellerIdAndOccurredAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsRawSalesEventAggregateRow(
                upper(e.eventType),
                count(e),
                coalesce(sum(e.grossAmount), 0L),
                coalesce(sum(e.netAmount), 0L)
            )
            from AnalyticsRawSalesEvent e
            where e.storeId = :storeId
              and e.occurredAt between :from and :to
            group by upper(e.eventType)
            """)
    List<AnalyticsRawSalesEventAggregateRow> aggregateByStoreIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsRawSalesEventAggregateRow(
                upper(e.eventType),
                count(e),
                coalesce(sum(e.grossAmount), 0L),
                coalesce(sum(e.netAmount), 0L)
            )
            from AnalyticsRawSalesEvent e
            where e.storeId = :storeId
              and e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            group by upper(e.eventType)
            """)
    List<AnalyticsRawSalesEventAggregateRow> aggregateByStoreIdAndSellerIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select max(coalesce(e.ingestedAt, e.occurredAt))
            from AnalyticsRawSalesEvent e
            where e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            """)
    LocalDateTime findLatestTimestampBySellerIdAndOccurredAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select max(coalesce(e.ingestedAt, e.occurredAt))
            from AnalyticsRawSalesEvent e
            where e.storeId = :storeId
              and e.occurredAt between :from and :to
            """)
    LocalDateTime findLatestTimestampByStoreIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select max(coalesce(e.ingestedAt, e.occurredAt))
            from AnalyticsRawSalesEvent e
            where e.storeId = :storeId
              and e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            """)
    LocalDateTime findLatestTimestampByStoreIdAndSellerIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
