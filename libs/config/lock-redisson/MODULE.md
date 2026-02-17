# Module: `libs/config/lock-redisson`

## 한눈에 보기
- 역할: `DistributedLockExecutor`의 Redisson 구현체를 제공합니다.
- 사용 시점: `:libs:config:lock`의 `@DistributedLock`를 Redis 기반으로 실행할 때 사용합니다.

## 모듈이 필요한 이유
락 코어(`lock`)와 저장소 구현체(`lock-redisson`)를 분리해, 락 전략 교체와 의존성 방향을 명확히 합니다.

## 적용 순서
1. `build.gradle.kts`에 `:libs:config:lock`, `:libs:config:lock-redisson`을 추가합니다.
2. `spring.data.redis.*` 설정을 채웁니다.

## application.yml 설정
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      database: 0
      ssl:
        enabled: false
```

- `password`, `username`은 비어 있으면 적용하지 않습니다.
- `database`는 기본 `0`입니다.
- `ssl.enabled=true`면 `rediss://`로 연결합니다.

## 간단 예시
```text
implementation(project(":libs:config:lock"))
implementation(project(":libs:config:lock-redisson"))
```
