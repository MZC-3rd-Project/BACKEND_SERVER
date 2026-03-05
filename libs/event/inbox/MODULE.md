# inbox

- 역할: Kafka listener가 적재한 이벤트를 Inbox 테이블 기준으로 push-first/poll-fallback 워커에서 처리합니다.
- 핵심 기능:
  - 중복 적재 방지(unique: consumer_name + event_id)
  - 즉시 트리거(push-first) + 스케줄 폴백(poll-fallback)
  - lease 기반 stale 복구 및 지수 백오프 재시도
  - 이벤트 타입별 `InboxEventHandler` 라우팅
