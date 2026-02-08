package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Tag(name = "7. 외부 호출 시뮬레이션", description = "WebClient로 자기 자신을 호출하여 Circuit Breaker, Retry 등을 실제 HTTP 호출로 테스트")
public interface ExternalCallApi {

    @Operation(summary = "WebClient → /api/test/health 호출",
            description = "Circuit Breaker로 감싸서 자기 자신의 health 엔드포인트를 HTTP 호출")
    @GetMapping("/external/health")
    ApiResponse<Map<String, Object>> callHealthWithCircuitBreaker();

    @Operation(summary = "WebClient → /api/test/items 호출 (Circuit Breaker + Retry)",
            description = "Circuit Breaker + Retry로 감싸서 아이템 목록 조회를 HTTP 호출")
    @GetMapping("/external/items")
    ApiResponse<Map<String, Object>> callItemsWithRetry();

    @Operation(summary = "WebClient → 존재하지 않는 엔드포인트 호출 (장애 시뮬레이션)",
            description = "의도적으로 실패하는 호출로 Circuit Breaker OPEN 상태 전환 테스트. 5번 이상 호출하면 서킷이 열림")
    @GetMapping("/external/fail")
    ApiResponse<Map<String, Object>> callFailingEndpoint();

    @Operation(summary = "WebClient → 느린 엔드포인트 호출 (Slow Call 테스트)",
            description = "지연이 있는 호출로 slowCallRateThreshold 테스트")
    @GetMapping("/external/slow")
    ApiResponse<Map<String, Object>> callSlowEndpoint(@RequestParam(defaultValue = "5000") long delayMs);
}
