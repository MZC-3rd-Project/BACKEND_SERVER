# Module: `libs/event/outbox-api`

## 한눈에 보기
- 역할: Outbox 공통 계약(설정/상태/이벤트)을 제공합니다.
- 사용 시점: Outbox 구현체와 도메인 코드 간 계약을 분리하고 싶을 때 사용합니다.

## 핵심 제공
- `OutboxProperties`
- `OutboxStatus`
- `OutboxSavedEvent`

## 간단 예시
```text
implementation(project(":libs:event:outbox-api"))
```
