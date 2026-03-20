package com.example.analyticsdashboard.repository;

public record AnalyticsJourneyEventCountRow(
        String eventType,
        String domainType,
        Long eventCount
) {
}
