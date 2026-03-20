package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsRawSearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalyticsRawSearchEventRepository extends JpaRepository<AnalyticsRawSearchEvent, Long> {

    Optional<AnalyticsRawSearchEvent> findByEventId(String eventId);

    List<AnalyticsRawSearchEvent> findByStoreIdAndOccurredAtBetween(
            Long storeId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<AnalyticsRawSearchEvent> findBySellerIdAndOccurredAtBetween(
            Long sellerId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<AnalyticsRawSearchEvent> findByStoreIdAndSellerIdAndOccurredAtBetween(
            Long storeId,
            Long sellerId,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsRawSearchEventCountRow(
                upper(e.eventType),
                count(e)
            )
            from AnalyticsRawSearchEvent e
            where e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            group by upper(e.eventType)
            """)
    List<AnalyticsRawSearchEventCountRow> aggregateBySellerIdAndOccurredAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsRawSearchEventCountRow(
                upper(e.eventType),
                count(e)
            )
            from AnalyticsRawSearchEvent e
            where e.storeId = :storeId
              and e.occurredAt between :from and :to
            group by upper(e.eventType)
            """)
    List<AnalyticsRawSearchEventCountRow> aggregateByStoreIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsRawSearchEventCountRow(
                upper(e.eventType),
                count(e)
            )
            from AnalyticsRawSearchEvent e
            where e.storeId = :storeId
              and e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            group by upper(e.eventType)
            """)
    List<AnalyticsRawSearchEventCountRow> aggregateByStoreIdAndSellerIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select max(coalesce(e.ingestedAt, e.occurredAt))
            from AnalyticsRawSearchEvent e
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
            from AnalyticsRawSearchEvent e
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
            from AnalyticsRawSearchEvent e
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
