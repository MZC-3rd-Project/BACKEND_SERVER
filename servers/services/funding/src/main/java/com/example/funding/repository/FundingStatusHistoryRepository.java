package com.example.funding.repository;

import com.example.funding.entity.FundingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingStatusHistoryRepository extends JpaRepository<FundingStatusHistory, Long> {

    List<FundingStatusHistory> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
