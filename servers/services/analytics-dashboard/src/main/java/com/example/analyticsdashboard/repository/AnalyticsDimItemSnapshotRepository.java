package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsDimItemSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalyticsDimItemSnapshotRepository extends JpaRepository<AnalyticsDimItemSnapshot, Long> {

    List<AnalyticsDimItemSnapshot> findByStoreId(Long storeId);

    List<AnalyticsDimItemSnapshot> findBySellerId(Long sellerId);

    List<AnalyticsDimItemSnapshot> findByStoreIdAndSellerId(Long storeId, Long sellerId);
}
