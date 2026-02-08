package com.example.testserver.service;

import com.example.config.resilience.CircuitBreakerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCallService {

    private final WebClient webClient;
    private final CircuitBreakerHelper circuitBreakerHelper;

    /**
     * [사용법] WebClient + CircuitBreaker
     * 자기 자신의 /api/test/health 엔드포인트를 HTTP로 호출
     * CircuitBreaker로 감싸서 장애 전파 방지
     */
    public Map<String, Object> callHealthWithCircuitBreaker() {
        String result = circuitBreakerHelper.executeWithCircuitBreaker(
                "external-health",
                () -> webClient.get()
                        .uri("/api/test/health")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)),
                () -> "{\"fallback\": true, \"message\": \"Health check unavailable\"}"
        );

        return Map.of(
                "endpoint", "/api/test/health",
                "pattern", "CircuitBreaker",
                "response", result,
                "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("external-health")
        );
    }

    /**
     * [사용법] WebClient + CircuitBreaker + Retry
     * 아이템 목록 조회를 HTTP로 호출
     * 실패 시 최대 3회 재시도 후 CircuitBreaker 판단
     */
    public Map<String, Object> callItemsWithRetry() {
        String result = circuitBreakerHelper.executeWithCircuitBreakerAndRetry(
                "external-items",
                () -> webClient.get()
                        .uri("/api/test/items")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)),
                () -> "{\"fallback\": true, \"message\": \"Items unavailable, returning cached\"}"
        );

        return Map.of(
                "endpoint", "/api/test/items",
                "pattern", "CircuitBreaker + Retry",
                "response", result,
                "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("external-items")
        );
    }

    /**
     * [사용법] 장애 시뮬레이션
     * 의도적으로 실패하는 엔드포인트 호출
     * 5회 이상 호출하면 CircuitBreaker가 OPEN 상태로 전환
     */
    public Map<String, Object> callFailingEndpoint() {
        try {
            String result = circuitBreakerHelper.executeWithCircuitBreaker(
                    "external-fail",
                    () -> webClient.get()
                            .uri("/internal/fail")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(5)),
                    () -> "Fallback: service degraded gracefully"
            );

            return Map.of(
                    "endpoint", "/internal/fail",
                    "pattern", "CircuitBreaker with Fallback",
                    "result", result,
                    "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("external-fail")
            );
        } catch (Exception e) {
            return Map.of(
                    "endpoint", "/internal/fail",
                    "pattern", "CircuitBreaker with Fallback",
                    "error", e.getMessage(),
                    "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("external-fail")
            );
        }
    }

    /**
     * [사용법] Slow Call 시뮬레이션
     * 지연이 있는 엔드포인트 호출
     * slowCallDurationThreshold(3초) 초과 시 slow call로 기록
     */
    public Map<String, Object> callSlowEndpoint(long delayMs) {
        long startTime = System.currentTimeMillis();
        try {
            String result = circuitBreakerHelper.executeWithCircuitBreaker(
                    "external-slow",
                    () -> webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/internal/slow")
                                    .queryParam("delayMs", delayMs)
                                    .build())
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(10)),
                    () -> "Fallback: slow service bypassed"
            );
            long elapsed = System.currentTimeMillis() - startTime;

            return Map.of(
                    "endpoint", "/internal/slow?delayMs=" + delayMs,
                    "pattern", "CircuitBreaker (Slow Call Detection)",
                    "result", result,
                    "elapsedMs", elapsed,
                    "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("external-slow")
            );
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return Map.of(
                    "endpoint", "/internal/slow?delayMs=" + delayMs,
                    "error", e.getMessage(),
                    "elapsedMs", elapsed,
                    "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("external-slow")
            );
        }
    }
}
