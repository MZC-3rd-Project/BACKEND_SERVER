# Module: `libs/event/outbox`

## 한눈에 보기
- 역할: Outbox 레거시 경로 호환용 wrapper 모듈입니다.
- 사용 시점: 기존 `:libs:event:outbox` 의존성을 당장 바꾸기 어려운 서비스에서 사용합니다.

## 내부 위임
- `:libs:event:outbox-api`
- `:libs:event:outbox-jpa-kafka`

## 권장
신규 코드에서는 아래를 직접 사용하세요.
```text
implementation(project(":libs:event:outbox-api"))
implementation(project(":libs:event:outbox-jpa-kafka"))
```
