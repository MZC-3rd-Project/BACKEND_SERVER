# Module: `libs/config/lock`

## 한눈에 보기
- 역할: 분산락 AOP(@DistributedLock) 구현을 제공합니다.
- 사용 시점: 동시성 경합이 있는 커맨드 메서드를 직렬화할 때 사용합니다.

## 모듈이 필요한 이유
공통 기능을 서비스마다 다시 만들면 구현이 조금씩 달라지고 유지보수 포인트가 급격히 늘어납니다.
이 모듈은 팀 공통 정책을 한 곳으로 모아 "중복 제거 + 일관성 유지 + 변경 비용 절감"을 만드는 목적입니다.

## 적용 순서
1. `build.gradle.kts`에 모듈 의존성을 추가합니다.
2. 필요한 설정 키를 `application.yml`에 채웁니다.
3. 기존 중복 코드를 모듈 API로 대체합니다.

## application.yml 설정
이 모듈(`lock`) 자체에는 전용 설정 키가 없습니다.  
실행 구현체(`lock-redisson`)를 함께 사용하면 `spring.data.redis.*` 설정이 필요합니다.

## 간단 예시
```text
implementation(project(":libs:config:lock"))
implementation(project(":libs:config:lock-redisson"))
@DistributedLock(key = "'stock:' + #request.stockItemId")
```
