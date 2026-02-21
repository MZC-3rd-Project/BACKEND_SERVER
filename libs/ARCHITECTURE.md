# Common Modules Architecture Guide

## 1. 목표
- 서비스별 도메인 로직과 공통 인프라 로직을 명확히 분리한다.
- 공통모듈은 "재사용 가능하고 안정적인 계약"만 제공한다.
- 모듈 간 순환 의존을 금지하고, 의존 방향을 단방향으로 유지한다.

## 2. 현재 구조 평가
- 현재 `libs`는 기능별로 이미 잘 분리되어 있다.
- 다만 일부 계약(예외/헤더/이벤트 트리거)이 하드코딩 또는 정적 상태에 의존해 드리프트 위험이 있다.
- `security`, `openapi`, `config` 계열에서 경계가 애매한 클래스가 섞일 가능성이 있어 분리 기준이 필요하다.

## 3. 모듈 분리 기준
- 아래 조건을 2개 이상 만족하면 새 모듈 분리를 우선 검토한다.
1. 소비 서비스가 3개 이상이고 변경 주기가 빠르다.
2. 런타임 의존(웹/시큐리티/DB/Kafka)이 강해서 모든 서비스가 필요로 하진 않는다.
3. API 계약(헤더, 응답, 에러코드)과 구현(필터, AOP, 스케줄러)이 한 모듈에 같이 있다.
4. 테스트 범위가 넓어져 작은 변경도 전체 회귀를 유발한다.

## 4. 권장 목표 구조
- `libs/core/*`: 순수 코어(예외, 식별자, 페이징, 유틸)
- `libs/contracts/*`: 서비스 간 공유되는 계약(HTTP 헤더 키, 이벤트 envelope, 공통 DTO 스키마)
- `libs/api/*`: API 응답/예외 핸들러
- `libs/security/*`: 시큐리티 컨텍스트/서명 검증/암복호화
- `libs/config/*`: 기술 설정(redis, kafka, resilience, tracing, shedlock, webclient)
- `libs/event/*`: 도메인 이벤트 인터페이스 + outbox 구현
- `libs/openapi/*`: 문서화 커스터마이징

## 5. 우선 분리 후보
1. Header contract 분리
- 완료: 헤더 키 상수는 `libs/contracts/http`로 일원화
- 이유: `openapi`와 `security`가 같은 계약을 쓰지만 구현 관심사가 다름

2. Security 내부 분리
- 후보: `security/core` -> `security/context`, `security/crypto`
- 이유: AuthContext/헤더검증과 암복호화 라이프사이클이 다름

3. Config lock 계층 분리
- 후보: `config/redis/lock`을 `config/lock`으로 분리
- 이유: 락 추상화는 저장소(redis)와 독립적으로 관리하는 편이 확장에 유리

## 6. 이번 반영과 정합성
- 인프라 예외를 `TechnicalException` 계열로 통일해 공통 에러 응답 계약과 일치시켰다.
- OpenAPI 헤더 정의를 보안 상수와 동일 소스로 맞춰 계약 드리프트를 줄였다.
- Outbox 이벤트 트리거를 엔티티 정적 호출에서 서비스 레이어 이벤트 발행으로 이동시켰다.

## 7. 단계별 마이그레이션
1. Contract 고정
- 헤더/이벤트/에러코드 계약을 중앙화하고 하드코딩 제거

2. 구현 분리
- 런타임 기술 의존이 큰 구현을 `config`, `security`, `event` 내부 세부 모듈로 이동

3. 소비자 정리
- 각 서비스의 `build.gradle.kts` 의존을 최소화
- 사용하지 않는 transitive 의존 제거

4. 테스트 보강
- 공통모듈별 스모크 테스트 추가
- 계약 변경 시 하위 서비스 영향 점검 체크리스트 운영

## 8. 의존 방향 규칙
- 허용: `contracts -> core`, `api -> core/contracts`, `security -> core/contracts`
- 허용: `config -> core`, `event -> core/data`
- 금지: `core -> config/security/event/openapi`
- 금지: `contracts -> runtime 구현 모듈(config/security/event)`

## 9. PR 운영 가이드
- 공통모듈 PR은 "계약 변경"과 "구현 리팩터링"을 분리한다.
- 서비스 영향도가 큰 변경은 샘플 사용처와 마이그레이션 가이드를 반드시 포함한다.
- 한 번에 대규모 이동보다 모듈 단위 점진 분리를 기본 전략으로 한다.
