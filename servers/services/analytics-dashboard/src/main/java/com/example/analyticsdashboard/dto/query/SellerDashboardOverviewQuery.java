package com.example.analyticsdashboard.dto.query;

import com.example.analyticsdashboard.exception.AnalyticsDashboardErrorCode;
import com.example.core.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public record SellerDashboardOverviewQuery(
        DashboardQueryMode mode,
        LocalDate date,
        YearMonth yearMonth,
        LocalDate from,
        LocalDate to,
        DashboardSeriesBucket bucket,
        ZoneId timezone
) {

    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Seoul");
    private static final long MAX_RANGE_DAYS = 93L;

    public static SellerDashboardOverviewQuery of(String mode,
                                                  String date,
                                                  String yearMonth,
                                                  String from,
                                                  String to,
                                                  String bucket,
                                                  String timezone) {
        DashboardQueryMode parsedMode = DashboardQueryMode.from(mode);
        ZoneId parsedTimezone = parseTimezone(timezone);

        return switch (parsedMode) {
            case DAILY -> buildDailyQuery(date, parsedTimezone);
            case MONTHLY -> buildMonthlyQuery(yearMonth, parsedTimezone);
            case RANGE -> buildRangeQuery(from, to, bucket, parsedTimezone);
        };
    }

    private static SellerDashboardOverviewQuery buildDailyQuery(String date, ZoneId timezone) {
        LocalDate parsedDate = parseDate(date, "date");
        return new SellerDashboardOverviewQuery(
                DashboardQueryMode.DAILY,
                parsedDate,
                null,
                null,
                null,
                null,
                timezone
        );
    }

    private static SellerDashboardOverviewQuery buildMonthlyQuery(String yearMonth, ZoneId timezone) {
        YearMonth parsedYearMonth = parseYearMonth(yearMonth, "yearMonth");
        return new SellerDashboardOverviewQuery(
                DashboardQueryMode.MONTHLY,
                null,
                parsedYearMonth,
                null,
                null,
                null,
                timezone
        );
    }

    private static SellerDashboardOverviewQuery buildRangeQuery(String from,
                                                                String to,
                                                                String bucket,
                                                                ZoneId timezone) {
        LocalDate parsedFrom = parseDate(from, "from");
        LocalDate parsedTo = parseDate(to, "to");

        if (parsedFrom.isAfter(parsedTo)) {
            throw invalid("from은 to보다 클 수 없습니다.");
        }

        long days = ChronoUnit.DAYS.between(parsedFrom, parsedTo) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw invalid("RANGE 조회는 최대 93일까지만 허용됩니다.");
        }

        return new SellerDashboardOverviewQuery(
                DashboardQueryMode.RANGE,
                null,
                null,
                parsedFrom,
                parsedTo,
                DashboardSeriesBucket.from(bucket),
                timezone
        );
    }

    private static LocalDate parseDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw invalid(fieldName + "는 필수입니다.");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw invalid(fieldName + "는 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    private static YearMonth parseYearMonth(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw invalid(fieldName + "는 필수입니다.");
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw invalid(fieldName + "는 yyyy-MM 형식이어야 합니다.");
        }
    }

    private static ZoneId parseTimezone(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(value.trim());
        } catch (Exception e) {
            throw invalid("timezone 값이 유효하지 않습니다.");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(AnalyticsDashboardErrorCode.INVALID_DASHBOARD_QUERY_PARAMETER, message);
    }
}
