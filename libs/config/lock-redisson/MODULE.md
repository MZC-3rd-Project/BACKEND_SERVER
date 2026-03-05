# Module: `libs/config/lock-redisson`

## 이 모듈이 해결하는 문제
`config/lock`의 락 계약을 실제 Redis 기반으로 실행하려면 구현체가 필요합니다.
`lock-redisson`은 Redisson 기반 `DistributedLockExecutor`를 제공합니다.

## 언제 사용하면 되나요?
- `@DistributedLock`를 실제 운영 환경에서 사용하려고 할 때
- Redis를 분산락 저장소로 사용할 때

## 빠른 시작
```text
implementation(project(":libs:config:lock"))
implementation(project(":libs:config:lock-redisson"))
```

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```
