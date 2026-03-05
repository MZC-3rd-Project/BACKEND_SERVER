# Module: `libs/config/redis`

## 이 모듈이 해결하는 문제
RedisTemplate/직렬화 설정을 서비스마다 반복 구현하면 장애 시 원인 파악이 어렵습니다.
`config/redis`는 Redis 접근 기본 설정을 공통화합니다.

## 언제 사용하면 되나요?
- 캐시, 세션, 임시 데이터 저장소로 Redis를 쓸 때
- 서비스 간 Redis 직렬화/접속 설정을 통일하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:config:redis"))
```

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
```
