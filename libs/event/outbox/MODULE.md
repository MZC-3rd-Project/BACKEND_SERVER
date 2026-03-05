# Module: `libs/event/outbox`

## 이 모듈은 무엇인가요?
`outbox`는 레거시 경로 호환용 wrapper 모듈입니다.
기존 서비스 의존성을 즉시 바꾸기 어려울 때 호환성을 유지합니다.

## 내부 위임
- `:libs:event:outbox-api`
- `:libs:event:outbox-jpa-kafka`

## 신규 코드 권장
```text
implementation(project(":libs:event:outbox-api"))
implementation(project(":libs:event:outbox-jpa-kafka"))
```
