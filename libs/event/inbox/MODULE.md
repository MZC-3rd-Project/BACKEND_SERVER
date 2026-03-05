# Module: `libs/event/inbox`

## 이 모듈이 해결하는 문제
Kafka 소비 후 후속 처리 로직에서 중복 처리/재시도/복구 로직을 직접 구현하면 복잡해집니다.
`inbox`는 inbox 테이블 기반 처리 파이프라인을 제공합니다.

## 언제 사용하면 되나요?
- 소비 이벤트를 DB에 적재 후 안정적으로 비동기 처리하고 싶을 때
- push-first + poll-fallback + stale 복구 패턴이 필요할 때

## 핵심 기능
- 중복 적재 방지 (`consumer_name + event_id` 유니크)
- 즉시 트리거 + 폴백 스케줄 처리
- lease 기반 stale 복구 / 지수 백오프 재시도
- 이벤트 타입별 `InboxEventHandler` 라우팅

## 빠른 시작
```text
implementation(project(":libs:event:inbox"))
```
