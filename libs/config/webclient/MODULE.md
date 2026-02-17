# Module: `libs/config/webclient`

## 한눈에 보기
- 역할: WebClient 공통 설정/로깅/예외 변환을 제공합니다.
- 사용 시점: 서비스 간 HTTP 호출 정책을 통일할 때 사용합니다.

## 모듈이 필요한 이유
공통 기능을 서비스마다 다시 만들면 구현이 조금씩 달라지고 유지보수 포인트가 급격히 늘어납니다.
이 모듈은 팀 공통 정책을 한 곳으로 모아 "중복 제거 + 일관성 유지 + 변경 비용 절감"을 만드는 목적입니다.

## 적용 순서
1. `build.gradle.kts`에 모듈 의존성을 추가합니다.
2. 필요한 설정 키를 `application.yml`에 채웁니다.
3. 기존 중복 코드를 모듈 API로 대체합니다.

## application.yml 설정
```yaml
app:
  webclient:
    connect-timeout: 5000
    read-timeout: 10000
    write-timeout: 10000
    max-in-memory-size: 2097152
```

## 간단 예시
```text
implementation(project(":libs:config:webclient"))
webClient.get().uri(...).retrieve()...
```
