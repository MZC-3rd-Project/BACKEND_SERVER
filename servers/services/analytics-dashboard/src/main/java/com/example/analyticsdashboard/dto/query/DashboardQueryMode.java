package com.example.analyticsdashboard.dto.query;

import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.core.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;

public enum DashboardQueryMode {

    DAILY,
    MONTHLY,
    RANGE;

    public static DashboardQueryMode from(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return DAILY;
        }
        try {
            return DashboardQueryMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER,
                    "mode는 DAILY, MONTHLY, RANGE 중 하나여야 합니다."
            );
        }
    }
}
