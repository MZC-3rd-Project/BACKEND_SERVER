# Module: `libs/config/kafka-idempotency-jpa`

## 이 모듈이 해결하는 문제
Kafka 이벤트는 중복 전달될 수 있어 멱등 처리와 실패 이력 저장이 필요합니다.
이 모듈은 JPA 기반으로 처리 상태/Dead Letter를 관리합니다.

## 언제 사용하면 되나요?
- 같은 이벤트를 한 번만 처리해야 할 때
- 처리 실패 이벤트를 DB에 저장하고 재처리하고 싶을 때

## 핵심 제공
- `KafkaConfig` (producer/consumer/error-handler 조립)
- `IdempotentConsumerService`
- `ProcessedEvent`, `DeadLetterMessage`
- `KafkaAutoConfiguration`

## 빠른 시작
```text
implementation(project(":libs:config:kafka-idempotency-jpa"))
```

```java
idempotentConsumerService.executeIdempotent(eventId, eventType, () -> handle());
```
