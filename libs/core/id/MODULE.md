# Module: `libs/core/id`

## 이 모듈이 해결하는 문제
분산 환경에서 충돌 없는 숫자 ID를 생성하려면 공통 정책이 필요합니다.
`core/id`는 Snowflake 기반 ID 생성기를 제공합니다.

## 언제 사용하면 되나요?
- 주문/결제/예약처럼 전역 유니크 ID가 필요할 때
- DB auto increment 대신 애플리케이션 ID 생성이 필요할 때

## 빠른 시작
```text
implementation(project(":libs:core:id"))
```

```yaml
app:
  snowflake:
    datacenter-id: 1
    worker-id: 1
```
