package com.example.analyticsdashboard.repository;

public record AnalyticsRawSalesEventAggregateRow(
        String eventType,
        Long eventCount,
        Long grossAmountSum,
        Long netAmountSum
) {
}
