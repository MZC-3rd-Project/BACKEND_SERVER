# Module: `libs/core/exception`

## 한눈에 보기
- 역할: 공통 예외 계층과 ErrorCode 계약을 제공합니다.
- 사용 시점: 도메인/인프라 예외를 공통 포맷으로 던질 때 사용합니다.

## 모듈이 필요한 이유
공통 기능을 서비스마다 다시 만들면 구현이 조금씩 달라지고 유지보수 포인트가 급격히 늘어납니다.
이 모듈은 팀 공통 정책을 한 곳으로 모아 "중복 제거 + 일관성 유지 + 변경 비용 절감"을 만드는 목적입니다.

## 적용 순서
1. `build.gradle.kts`에 모듈 의존성을 추가합니다.
2. 필요한 설정 키를 `application.yml`에 채웁니다.
3. 기존 중복 코드를 모듈 API로 대체합니다.

## 간단 예시
```text
implementation(project(":libs:core:exception"))
throw new BusinessException(DomainErrorCode.NOT_FOUND);
```
