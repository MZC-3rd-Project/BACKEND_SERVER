package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsJourneyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsJourneyEventRepository extends JpaRepository<AnalyticsJourneyEvent, Long> {

    boolean existsByEventIdAndEventSequence(String eventId, Integer eventSequence);

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

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsJourneyEventCountRow(
                upper(e.eventType),
                upper(e.domainType),
                count(e)
            )
            from AnalyticsJourneyEvent e
            where e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            group by upper(e.eventType), upper(e.domainType)
            """)
    List<AnalyticsJourneyEventCountRow> aggregateCountsBySellerIdAndOccurredAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsJourneyEventCountRow(
                upper(e.eventType),
                upper(e.domainType),
                count(e)
            )
            from AnalyticsJourneyEvent e
            where e.storeId = :storeId
              and e.occurredAt between :from and :to
            group by upper(e.eventType), upper(e.domainType)
            """)
    List<AnalyticsJourneyEventCountRow> aggregateCountsByStoreIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsJourneyEventCountRow(
                upper(e.eventType),
                upper(e.domainType),
                count(e)
            )
            from AnalyticsJourneyEvent e
            where e.storeId = :storeId
              and e.sellerId = :sellerId
              and e.occurredAt between :from and :to
            group by upper(e.eventType), upper(e.domainType)
            """)
    List<AnalyticsJourneyEventCountRow> aggregateCountsByStoreIdAndSellerIdAndOccurredAtBetween(
            @Param("storeId") Long storeId,
            @Param("sellerId") Long sellerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select max(coalesce(e.ingestedAt, e.occurredAt))
            from AnalyticsJourneyEvent e
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
            from AnalyticsJourneyEvent e
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
            from AnalyticsJourneyEvent e
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

    @Query("""
            select max(coalesce(e.ingestedAt, e.occurredAt))
            from AnalyticsJourneyEvent e
            """)
    LocalDateTime findLatestTimestamp();
}
