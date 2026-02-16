# Module: `libs/event/outbox`

## 한눈에 보기
- 역할: Outbox 저장/즉시 발행/릴레이 재시도 구조를 제공합니다.
- 사용 시점: 이벤트 유실 없이 비동기 전파 안정성을 확보할 때 사용합니다.

## 모듈이 필요한 이유
공통 기능을 서비스마다 다시 만들면 구현이 조금씩 달라지고 유지보수 포인트가 급격히 늘어납니다.
이 모듈은 팀 공통 정책을 한 곳으로 모아 "중복 제거 + 일관성 유지 + 변경 비용 절감"을 만드는 목적입니다.

## 적용 순서
1. `build.gradle.kts`에 모듈 의존성을 추가합니다.
2. 필요한 설정 키를 `application.yml`에 채웁니다.
3. 기존 중복 코드를 모듈 API로 대체합니다.

## application.yml 설정
```yaml
app:
  outbox:
    enabled: true
    immediate-publish-enabled: true
    relay:
      enabled: true
      fixed-delay-ms: 5000
      fetch-before-seconds: 5
      batch-size: 100
      max-in-flight: 32
      max-retries: 5
      max-error-message-length: 240
      sending-stale-threshold-seconds: 120
    cleanup:
      enabled: true
      cron: "0 0 3 * * *"
      retention-days: 7
```

## 간단 예시
```text
implementation(project(":libs:event:outbox"))
OutboxService가 메시지 저장 후 발행 이벤트 트리거
```
