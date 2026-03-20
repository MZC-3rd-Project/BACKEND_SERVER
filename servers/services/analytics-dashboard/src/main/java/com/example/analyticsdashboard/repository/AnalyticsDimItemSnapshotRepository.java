package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsDimItemSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalyticsDimItemSnapshotRepository extends JpaRepository<AnalyticsDimItemSnapshot, Long> {

    List<AnalyticsDimItemSnapshot> findByStoreId(Long storeId);

    List<AnalyticsDimItemSnapshot> findBySellerId(Long sellerId);

    List<AnalyticsDimItemSnapshot> findByStoreIdAndSellerId(Long storeId, Long sellerId);

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsItemStatusCountRow(
                upper(s.itemStatus),
                count(s)
            )
            from AnalyticsDimItemSnapshot s
            where s.sellerId = :sellerId
            group by upper(s.itemStatus)
            """)
    List<AnalyticsItemStatusCountRow> countByStatusForSellerId(@Param("sellerId") Long sellerId);

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsItemStatusCountRow(
                upper(s.itemStatus),
                count(s)
            )
            from AnalyticsDimItemSnapshot s
            where s.storeId = :storeId
            group by upper(s.itemStatus)
            """)
    List<AnalyticsItemStatusCountRow> countByStatusForStoreId(@Param("storeId") Long storeId);

    @Query("""
            select new com.example.analyticsdashboard.repository.AnalyticsItemStatusCountRow(
                upper(s.itemStatus),
                count(s)
            )
            from AnalyticsDimItemSnapshot s
            where s.storeId = :storeId
              and s.sellerId = :sellerId
            group by upper(s.itemStatus)
            """)
    List<AnalyticsItemStatusCountRow> countByStatusForStoreIdAndSellerId(
            @Param("storeId") Long storeId,
            @Param("sellerId") Long sellerId
    );
}
