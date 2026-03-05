# Module: `libs/event/outbox-api`

## 이 모듈이 해결하는 문제
outbox 구현체와 도메인 코드가 강하게 결합되면 교체/테스트가 어렵습니다.
`outbox-api`는 outbox 계약(설정/상태/이벤트)만 분리해서 제공합니다.

## 언제 사용하면 되나요?
- outbox 상태/이벤트 타입을 공통 계약으로 쓰고 싶을 때
- 구현체(`outbox-jpa-kafka`)와 경계를 분리하고 싶을 때

## 핵심 제공
- `OutboxProperties`
- `OutboxStatus`
- `OutboxSavedEvent`

## 빠른 시작
```text
implementation(project(":libs:event:outbox-api"))
```
