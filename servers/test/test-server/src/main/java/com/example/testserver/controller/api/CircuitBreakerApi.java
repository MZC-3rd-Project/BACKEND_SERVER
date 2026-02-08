package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Tag(name = "6. Circuit Breaker", description = "Resilience4j 서킷 브레이커 테스트")
public interface CircuitBreakerApi {

    @Operation(summary = "서킷 브레이커 테스트", description = "shouldFail=true로 반복 호출하면 서킷이 OPEN 상태로 전환")
    @GetMapping("/circuit-breaker")
    ApiResponse<Map<String, Object>> testCircuitBreaker(@RequestParam(defaultValue = "false") boolean shouldFail);

    @Operation(summary = "서킷 브레이커 상태 조회", description = "CLOSED → OPEN → HALF_OPEN 상태 확인")
    @GetMapping("/circuit-breaker/state")
    ApiResponse<Map<String, String>> getCircuitBreakerState(@RequestParam(defaultValue = "test-service") String name);
}
