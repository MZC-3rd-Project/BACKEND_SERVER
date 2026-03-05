# Module: `libs/config/resilience`

## 이 모듈이 해결하는 문제
외부 의존 시스템 장애가 곧바로 서비스 장애로 번지는 것을 막기 어렵습니다.
`config/resilience`는 CircuitBreaker/Retry 공통 설정을 제공합니다.

## 언제 사용하면 되나요?
- 외부 호출 실패 시 fallback이 필요할 때
- 재시도 정책을 팀 공통으로 적용하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:config:resilience"))
```

```yaml
resilience4j:
  circuitbreaker:
    instances:
      default:
        sliding-window-size: 10
  retry:
    instances:
      default:
        max-attempts: 3
```
