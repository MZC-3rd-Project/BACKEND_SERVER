package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsAggSellerKpiMonthly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalyticsAggSellerKpiMonthlyRepository extends JpaRepository<AnalyticsAggSellerKpiMonthly, Long> {

    Optional<AnalyticsAggSellerKpiMonthly> findBySellerIdAndBusinessMonth(Long sellerId, String businessMonth);

    List<AnalyticsAggSellerKpiMonthly> findBySellerIdAndBusinessMonthBetweenOrderByBusinessMonthAsc(
            Long sellerId,
            String fromMonth,
            String toMonth
    );
}
