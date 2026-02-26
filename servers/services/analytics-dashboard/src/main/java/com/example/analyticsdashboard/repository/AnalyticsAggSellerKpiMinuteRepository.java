package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsAggSellerKpiMinute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalyticsAggSellerKpiMinuteRepository extends JpaRepository<AnalyticsAggSellerKpiMinute, Long> {

    Optional<AnalyticsAggSellerKpiMinute> findByStoreIdAndBucketMinute(Long storeId, LocalDateTime bucketMinute);

    List<AnalyticsAggSellerKpiMinute> findByStoreIdAndBucketMinuteBetweenOrderByBucketMinuteAsc(
            Long storeId,
            LocalDateTime from,
            LocalDateTime to
    );
}
