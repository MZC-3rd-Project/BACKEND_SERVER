package com.example.analyticsdashboard.exception;

import com.example.core.exception.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalyticsDashboardErrorCode implements DomainErrorCode {

    INVALID_DASHBOARD_QUERY_PARAMETER("ANALYTICS-001", "대시보드 조회 파라미터가 유효하지 않습니다", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
