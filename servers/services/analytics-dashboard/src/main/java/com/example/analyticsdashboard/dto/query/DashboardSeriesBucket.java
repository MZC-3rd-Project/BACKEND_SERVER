package com.example.analyticsdashboard.dto.query;

import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.core.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;

public enum DashboardSeriesBucket {

    DAY,
    MONTH;

    public static DashboardSeriesBucket from(String rawBucket) {
        if (!StringUtils.hasText(rawBucket)) {
            return DAY;
        }
        try {
            return DashboardSeriesBucket.valueOf(rawBucket.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER,
                    "bucket은 DAY, MONTH 중 하나여야 합니다."
            );
        }
    }
}
