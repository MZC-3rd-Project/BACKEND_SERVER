package com.example.testserver.service;

import com.example.config.kafka.DeadLetterMessage;
import com.example.config.kafka.DeadLetterMessageRepository;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.config.resilience.CircuitBreakerHelper;
import com.example.config.resilience.FallbackRegistry;
import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.event.outbox.OutboxMessage;
import com.example.event.outbox.OutboxRepository;
import com.example.event.outbox.OutboxStatus;
import com.example.testserver.domain.TestItem;
import com.example.testserver.domain.TestItemRepository;
import com.example.testserver.event.TestItemCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestService {

    private final TestItemRepository testItemRepository;
    private final EventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OutboxRepository outboxRepository;
    private final DeadLetterMessageRepository deadLetterMessageRepository;
    private final IdempotentConsumerService idempotentConsumerService;
    private final CircuitBreakerHelper circuitBreakerHelper;
    private final FallbackRegistry fallbackRegistry;

    // ─── DB + Outbox 테스트 ─────────────────────────────────

    /**
     * [공통모듈 사용법] BaseEntity + Outbox 패턴
     * 1. TestItem은 BaseEntity를 상속 → createdAt, updatedAt 자동 관리
     * 2. EventPublisher.publish()로 이벤트 발행 → OutboxMessage 테이블에 저장
     * 3. 트랜잭션 커밋 후 ImmediatePublisher가 Kafka로 즉시 발행 시도
     * 4. 실패 시 OutboxRelayScheduler가 5초 간격으로 재시도
     */
    @Transactional
    public TestItem createItem(String name, String description) {
        TestItem item = TestItem.create(name, description);
        testItemRepository.save(item);

        TestItemCreatedEvent event = new TestItemCreatedEvent(item.getId(), item.getName());
        eventPublisher.publish(event, EventMetadata.of("TestItem", String.valueOf(item.getId())));

        log.info("[Outbox] TestItem created and event published: id={}, name={}", item.getId(), item.getName());
        return item;
    }

    public List<TestItem> findAll() {
        return testItemRepository.findAll();
    }

    public TestItem findById(Long id) {
        return testItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    // ─── Redis 캐시 테스트 ──────────────────────────────────

    /**
     * [공통모듈 사용법] RedisTemplate
     * - RedisConfig에서 자동 설정된 RedisTemplate 주입
     * - Key: StringSerializer, Value: JSON Serializer
     * - TTL 5분으로 캐시
     */
    public void cacheValue(String key, String value) {
        redisTemplate.opsForValue().set("test:" + key, value, 5, TimeUnit.MINUTES);
        log.info("[Redis] Cached: key={}, value={}", key, value);
    }

    public Object getCachedValue(String key) {
        return redisTemplate.opsForValue().get("test:" + key);
    }

    public Boolean deleteCachedValue(String key) {
        return redisTemplate.delete("test:" + key);
    }

    // ─── Outbox 상태 조회 ───────────────────────────────────

    /**
     * [공통모듈 사용법] OutboxRepository 직접 조회
     * - PENDING: 아직 Kafka로 발행되지 않은 메시지
     * - PUBLISHED: 발행 완료
     * - FAILED: 최대 재시도 초과
     */
    public List<OutboxMessage> getOutboxMessages(OutboxStatus status) {
        return outboxRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    public List<OutboxMessage> getAllOutboxMessages() {
        return outboxRepository.findAll();
    }

    // ─── DLQ 조회 ───────────────────────────────────────────

    /**
     * [공통모듈 사용법] DeadLetterMessageRepository
     * - 처리 실패한 Kafka 메시지가 저장되는 테이블
     * - 관리자가 확인 후 재처리 가능
     */
    public List<DeadLetterMessage> getAllDeadLetters() {
        return deadLetterMessageRepository.findAll();
    }

    // ─── 멱등성 소비자 테스트 ───────────────────────────────

    /**
     * [공통모듈 사용법] IdempotentConsumerService
     * - 동일 eventId로 여러 번 호출해도 한 번만 처리
     * - processed_events 테이블에 처리 상태 기록
     */
    public Map<String, Object> testIdempotentProcessing(String eventId) {
        Optional<String> result = idempotentConsumerService.executeIdempotent(
                eventId,
                "TEST_IDEMPOTENT",
                () -> {
                    log.info("[Idempotent] Processing event: {}", eventId);
                    return "processed-" + eventId;
                }
        );

        return Map.of(
                "eventId", eventId,
                "processed", result.isPresent(),
                "result", result.orElse("skipped (duplicate)")
        );
    }

    // ─── Circuit Breaker 테스트 ─────────────────────────────

    /**
     * [공통모듈 사용법] CircuitBreakerHelper
     * - executeWithCircuitBreaker: 서킷 브레이커만 적용
     * - executeWithRetry: 재시도만 적용
     * - executeWithCircuitBreakerAndRetry: 서킷 브레이커 + 재시도
     * - 두 번째 인자로 fallback 제공 가능
     */
    public Map<String, Object> testCircuitBreaker(boolean shouldFail) {
        try {
            String result = circuitBreakerHelper.executeWithCircuitBreakerAndRetry(
                    "test-service",
                    () -> {
                        if (shouldFail) {
                            throw new RuntimeException("Simulated failure for circuit breaker test");
                        }
                        return "Circuit breaker test success!";
                    },
                    () -> "Fallback response (service degraded)"
            );

            return Map.of(
                    "result", result,
                    "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("test-service")
            );
        } catch (Exception e) {
            return Map.of(
                    "error", e.getMessage(),
                    "circuitBreakerState", circuitBreakerHelper.getCircuitBreakerState("test-service")
            );
        }
    }

    public String getCircuitBreakerState(String name) {
        return circuitBreakerHelper.getCircuitBreakerState(name);
    }
}
