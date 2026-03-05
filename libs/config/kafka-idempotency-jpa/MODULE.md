# Module: `libs/config/kafka-idempotency-jpa`

## 한눈에 보기
- 역할: Kafka 소비 멱등성/재처리 상태를 JPA로 관리합니다.
- 사용 시점: 중복 이벤트 처리 방지와 실패 재시도 상태 추적이 필요할 때 사용합니다.

## 핵심 제공
- `KafkaConfig` (producer/consumer/error-handler 조립)
- `IdempotentConsumerService`
- `ProcessedEvent`, `DeadLetterMessage` 엔티티/리포지토리
- `KafkaAutoConfiguration`

## 간단 예시
```text
implementation(project(":libs:config:kafka-idempotency-jpa"))
idempotentConsumerService.executeIdempotent(eventId, eventType, () -> handle());
```
