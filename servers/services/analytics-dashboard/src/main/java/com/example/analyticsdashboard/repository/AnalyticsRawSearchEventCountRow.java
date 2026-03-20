package com.example.analyticsdashboard.repository;

public record AnalyticsRawSearchEventCountRow(
        String eventType,
        Long eventCount
) {
}
