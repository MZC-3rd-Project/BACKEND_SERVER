# Module: `libs/event/domain`

## 이 모듈이 해결하는 문제
도메인 이벤트 계약이 서비스마다 다르면 이벤트 발행/구독 규칙이 쉽게 깨집니다.
`event/domain`은 이벤트 기본 인터페이스와 메타데이터 계약을 제공합니다.

## 언제 사용하면 되나요?
- 서비스 내부 이벤트를 표준 계약으로 발행하고 싶을 때
- outbox/inbox와 결합되는 이벤트 모델의 기준이 필요할 때

## 빠른 시작
```text
implementation(project(":libs:event:domain"))
```

```java
eventPublisher.publish(event, metadata);
```
