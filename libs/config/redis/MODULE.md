# Module: `libs/config/redis`

## 한눈에 보기
- 역할: RedisTemplate/CacheManager 설정을 제공합니다.
- 사용 시점: 캐시/큐/임시 상태 저장에 Redis를 사용할 때 사용합니다.

## 모듈이 필요한 이유
공통 기능을 서비스마다 다시 만들면 구현이 조금씩 달라지고 유지보수 포인트가 급격히 늘어납니다.
이 모듈은 팀 공통 정책을 한 곳으로 모아 "중복 제거 + 일관성 유지 + 변경 비용 절감"을 만드는 목적입니다.

## 적용 순서
1. `build.gradle.kts`에 모듈 의존성을 추가합니다.
2. 필요한 설정 키를 `application.yml`에 채웁니다.
3. 기존 중복 코드를 모듈 API로 대체합니다.

## application.yml 설정
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
```

## 간단 예시
```text
implementation(project(":libs:config:redis"))
StringRedisTemplate ops 사용
```
