package com.example.testserver.controller;

import com.example.api.response.ApiResponse;
import com.example.config.kafka.DeadLetterMessage;
import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.core.id.Snowflake;
import com.example.event.outbox.OutboxMessage;
import com.example.event.outbox.OutboxStatus;
import com.example.testserver.controller.api.*;
import com.example.testserver.dto.TestItemRequest;
import com.example.testserver.dto.TestItemResponse;
import com.example.testserver.exception.TestErrorCode;
import com.example.testserver.service.ExternalCallService;
import com.example.testserver.service.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController implements
        HealthApi, ItemApi, OutboxApi, DlqApi,
        CacheApi, IdempotentApi, CircuitBreakerApi,
        ExternalCallApi, ErrorApi, SnowflakeApi {

    private final TestService testService;
    private final ExternalCallService externalCallService;
    private final Snowflake snowflake;

    // ─── Health ─────────────────────────────────────────────

    @Override
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "service", "test-server",
                "description", "공통 모듈 통합 테스트 서버"
        ));
    }

    @Override
    public ApiResponse<List<Map<String, String>>> listModules() {
        return ApiResponse.success(List.of(
                Map.of("module", "libs:core:exception", "description", "ErrorCode sealed interface + 예외 계층"),
                Map.of("module", "libs:core:util", "description", "JsonUtils (ObjectMapper 래퍼)"),
                Map.of("module", "libs:api:response", "description", "ApiResponse, ErrorResponse, PageResponse"),
                Map.of("module", "libs:api:exception-handler", "description", "GlobalExceptionHandler"),
                Map.of("module", "libs:data:entity", "description", "BaseEntity (JPA Auditing + Soft Delete)"),
                Map.of("module", "libs:security:core", "description", "HMAC-SHA256 서명, 암호화, PII 마스킹"),
                Map.of("module", "libs:config:kafka", "description", "KafkaTemplate, 멱등성 소비자, DLQ"),
                Map.of("module", "libs:config:redis", "description", "RedisTemplate, CacheManager"),
                Map.of("module", "libs:config:resilience", "description", "Circuit Breaker, Retry, Fallback"),
                Map.of("module", "libs:event:domain", "description", "DomainEvent, EventPublisher, EventMetadata"),
                Map.of("module", "libs:event:outbox", "description", "Transactional Outbox Pattern"),
                Map.of("module", "libs:openapi:config", "description", "Swagger UI 자동 설정"),
                Map.of("module", "libs:core:id", "description", "Snowflake ID (Long→String 직렬화, JPA 통합)")
        ));
    }

    // ─── Items (Entity → DTO + @SnowflakeId 직렬화) ────────

    @Override
    public ApiResponse<TestItemResponse> createItem(TestItemRequest request) {
        return ApiResponse.success(
                TestItemResponse.from(testService.createItem(request.getName(), request.getDescription())));
    }

    @Override
    public ApiResponse<List<TestItemResponse>> listItems() {
        return ApiResponse.success(
                testService.findAll().stream().map(TestItemResponse::from).toList());
    }

    @Override
    public ApiResponse<TestItemResponse> getItem(Long id) {
        return ApiResponse.success(
                TestItemResponse.from(testService.findById(id)));
    }

    // ─── Outbox ─────────────────────────────────────────────

    @Override
    public ApiResponse<List<OutboxMessage>> getAllOutbox() {
        return ApiResponse.success(testService.getAllOutboxMessages());
    }

    @Override
    public ApiResponse<List<OutboxMessage>> getOutboxByStatus(String status) {
        return ApiResponse.success(testService.getOutboxMessages(OutboxStatus.valueOf(status.toUpperCase())));
    }

    // ─── DLQ ────────────────────────────────────────────────

    @Override
    public ApiResponse<List<DeadLetterMessage>> getAllDeadLetters() {
        return ApiResponse.success(testService.getAllDeadLetters());
    }

    // ─── Cache ──────────────────────────────────────────────

    @Override
    public ApiResponse<Void> cacheValue(Map<String, String> request) {
        testService.cacheValue(request.get("key"), request.get("value"));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Object> getCachedValue(String key) {
        return ApiResponse.success(testService.getCachedValue(key));
    }

    @Override
    public ApiResponse<Boolean> deleteCachedValue(String key) {
        return ApiResponse.success(testService.deleteCachedValue(key));
    }

    // ─── Idempotent ─────────────────────────────────────────

    @Override
    public ApiResponse<Map<String, Object>> testIdempotent(String eventId) {
        return ApiResponse.success(testService.testIdempotentProcessing(eventId));
    }

    // ─── Circuit Breaker ────────────────────────────────────

    @Override
    public ApiResponse<Map<String, Object>> testCircuitBreaker(boolean shouldFail) {
        return ApiResponse.success(testService.testCircuitBreaker(shouldFail));
    }

    @Override
    public ApiResponse<Map<String, String>> getCircuitBreakerState(String name) {
        return ApiResponse.success(Map.of("name", name, "state", testService.getCircuitBreakerState(name)));
    }

    // ─── External Call (WebClient + CircuitBreaker) ─────────

    @Override
    public ApiResponse<Map<String, Object>> callHealthWithCircuitBreaker() {
        return ApiResponse.success(externalCallService.callHealthWithCircuitBreaker());
    }

    @Override
    public ApiResponse<Map<String, Object>> callItemsWithRetry() {
        return ApiResponse.success(externalCallService.callItemsWithRetry());
    }

    @Override
    public ApiResponse<Map<String, Object>> callFailingEndpoint() {
        return ApiResponse.success(externalCallService.callFailingEndpoint());
    }

    @Override
    public ApiResponse<Map<String, Object>> callSlowEndpoint(long delayMs) {
        return ApiResponse.success(externalCallService.callSlowEndpoint(delayMs));
    }

    // ─── Error ──────────────────────────────────────────────

    @Override
    public ApiResponse<Void> testBusinessError() {
        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    @Override
    public ApiResponse<Void> testTechnicalError() {
        throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
    }

    @Override
    public ApiResponse<Void> testUnexpectedError() {
        throw new RuntimeException("Unexpected error for testing");
    }

    @Override
    public ApiResponse<Void> testDomainError(String errorType) {
        TestErrorCode errorCode = switch (errorType.toUpperCase()) {
            case "NOT_FOUND" -> TestErrorCode.TEST_ITEM_NOT_FOUND;
            case "ALREADY_EXISTS" -> TestErrorCode.TEST_ITEM_ALREADY_EXISTS;
            case "NAME_REQUIRED" -> TestErrorCode.TEST_ITEM_NAME_REQUIRED;
            case "LIMIT_EXCEEDED" -> TestErrorCode.TEST_ITEM_LIMIT_EXCEEDED;
            default -> throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "Unknown error type: " + errorType + ". Available: NOT_FOUND, ALREADY_EXISTS, NAME_REQUIRED, LIMIT_EXCEEDED");
        };
        throw new BusinessException(errorCode);
    }

    // ─── Snowflake ID ────────────────────────────────────────

    @Override
    public ApiResponse<Map<String, Object>> generateId() {
        long id = snowflake.nextId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(id));
        result.put("idAsLong", id);
        result.put("timestamp", Snowflake.extractTimestamp(id).toString());
        result.put("datacenterId", Snowflake.extractDatacenterId(id));
        result.put("workerId", Snowflake.extractWorkerId(id));
        result.put("sequence", Snowflake.extractSequence(id));
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<List<Map<String, Object>>> generateBatchIds() {
        List<Map<String, Object>> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long id = snowflake.nextId();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("id", String.valueOf(id));
            entry.put("timestamp", Snowflake.extractTimestamp(id).toString());
            ids.add(entry);
        }
        return ApiResponse.success(ids);
    }

    @Override
    public ApiResponse<Map<String, Object>> parseId(String id) {
        long longId = Long.parseLong(id);
        Instant timestamp = Snowflake.extractTimestamp(longId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("idAsLong", longId);
        result.put("timestamp", timestamp.toString());
        result.put("datacenterId", Snowflake.extractDatacenterId(longId));
        result.put("workerId", Snowflake.extractWorkerId(longId));
        result.put("sequence", Snowflake.extractSequence(longId));
        return ApiResponse.success(result);
    }
}
