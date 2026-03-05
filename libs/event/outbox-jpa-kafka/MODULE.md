# Module: `libs/event/outbox-jpa-kafka`

## 한눈에 보기
- 역할: JPA + Kafka 기반 Outbox 구현체를 제공합니다.
- 사용 시점: 트랜잭션 내 이벤트 저장 후 릴레이/즉시 발행/정리 스케줄러가 필요할 때 사용합니다.

## 핵심 제공
- `OutboxService`
- `ImmediatePublisher`, `OutboxRelayScheduler`, `OutboxCleanupScheduler`
- `OutboxAutoConfiguration`

## 간단 예시
```text
implementation(project(":libs:event:outbox-jpa-kafka"))
```
