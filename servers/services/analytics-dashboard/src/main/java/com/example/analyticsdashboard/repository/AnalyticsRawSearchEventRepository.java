package com.example.analyticsdashboard.repository;

import com.example.analyticsdashboard.entity.AnalyticsRawSearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalyticsRawSearchEventRepository extends JpaRepository<AnalyticsRawSearchEvent, Long> {

    Optional<AnalyticsRawSearchEvent> findByEventId(String eventId);
}
