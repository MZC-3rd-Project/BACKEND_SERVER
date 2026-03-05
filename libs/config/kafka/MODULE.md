# Module: `libs/config/kafka`

## 한눈에 보기
- 역할: Kafka 레거시 경로 호환용 wrapper 모듈입니다.
- 사용 시점: 기존 `:libs:config:kafka` 의존성을 즉시 변경하기 어려운 경우 사용합니다.

## 내부 위임
- `:libs:config:kafka-core`
- `:libs:config:kafka-idempotency-jpa`

## 권장
신규 코드에서는 역할별 모듈을 직접 사용하세요.
```text
implementation(project(":libs:config:kafka-core"))
implementation(project(":libs:config:kafka-idempotency-jpa"))
```
