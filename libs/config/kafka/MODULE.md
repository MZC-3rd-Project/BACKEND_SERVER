# Module: `libs/config/kafka`

## 이 모듈은 무엇인가요?
`kafka`는 레거시 경로 호환을 위한 wrapper 모듈입니다.
기존 서비스가 깨지지 않도록 남겨둔 호환 레이어입니다.

## 내부에서 실제로 쓰는 모듈
- `:libs:config:kafka-core`
- `:libs:config:kafka-idempotency-jpa`

## 신규 코드 권장
```text
implementation(project(":libs:config:kafka-core"))
implementation(project(":libs:config:kafka-idempotency-jpa"))
```
