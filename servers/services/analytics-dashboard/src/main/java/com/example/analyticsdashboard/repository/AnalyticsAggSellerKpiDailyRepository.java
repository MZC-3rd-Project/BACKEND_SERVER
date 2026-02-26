package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsAggSellerKpiDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AnalyticsAggSellerKpiDailyRepository extends JpaRepository<AnalyticsAggSellerKpiDaily, Long> {

    Optional<AnalyticsAggSellerKpiDaily> findBySellerIdAndBusinessDate(Long sellerId, LocalDate businessDate);

    List<AnalyticsAggSellerKpiDaily> findBySellerIdAndBusinessDateBetweenOrderByBusinessDateAsc(
            Long sellerId,
            LocalDate from,
            LocalDate to
    );
}
