# Module: `libs/event/outbox-jpa-kafka`

## 이 모듈이 해결하는 문제
트랜잭션 내 이벤트 저장, 발행 재시도, 정리 스케줄을 직접 구현하면 코드가 커지고 장애 대응이 어렵습니다.
`outbox-jpa-kafka`는 JPA + Kafka 기반 outbox 구현체를 제공합니다.

## 언제 사용하면 되나요?
- DB 트랜잭션과 이벤트 발행을 안전하게 연결하고 싶을 때
- relay/cleanup 스케줄까지 표준 구현으로 가져가고 싶을 때

## 핵심 제공
- `OutboxService`
- `ImmediatePublisher`
- `OutboxRelayScheduler`
- `OutboxCleanupScheduler`
- `OutboxAutoConfiguration`

## 빠른 시작
```text
implementation(project(":libs:event:outbox-jpa-kafka"))
```
