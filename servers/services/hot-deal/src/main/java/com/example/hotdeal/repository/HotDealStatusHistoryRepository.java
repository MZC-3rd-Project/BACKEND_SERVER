package com.example.hotdeal.repository;

import com.example.hotdeal.entity.HotDealStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotDealStatusHistoryRepository extends JpaRepository<HotDealStatusHistory, Long> {

    List<HotDealStatusHistory> findByHotDealIdOrderByCreatedAtDesc(Long hotDealId);
}
