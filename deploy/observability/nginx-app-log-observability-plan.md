# Nginx + Application 로그 통합 관측 설계안

작성일: 2026-03-23  
작성자: Codex

## 1. 목적

이 문서는 `edge-nginx` 로그와 애플리케이션 로그를 Loki / Grafana에서 함께 관리하기 위한 설계 판단 기준과 단계별 추진 방안을 정리한다.

## 2. 현재 상태 요약

- `edge-nginx`는 별도 런타임이며 `client-gateway` 앞단 reverse proxy 역할을 한다.
- 현재 `edge-nginx` access log는 텍스트 포맷 `edge_demo`다.
- 애플리케이션 쪽은 서비스별 `logging.level` 설정은 있지만 공통 JSON 로그 포맷은 없다.
- `libs/config/tracing` 모듈은 존재하고 `micrometer-tracing-bridge-brave`를 사용한다.
- Servlet 환경에서는 `traceId`, `spanId`를 MDC에 넣는 필터가 있다.
- `client-gateway`는 WebFlux라 Servlet 필터만으로는 동일한 방식으로 동작하지 않는다.

## 3. 가능한가

가능하다. 다만 아래처럼 구분해야 한다.

- 바로 가능한 것
  - `nginx` 로그를 JSON으로 구조화
  - 앱 로그를 JSON으로 구조화
  - Alloy가 둘 다 수집해서 Loki로 전송
  - Grafana에서 `service`, `requestId`, `traceId` 기준 조회
- 추가 작업이 필요한 것
  - 공통 JSON logback 설정
  - 공통 `requestId` 정책
  - tracing 모듈의 전 서비스 의존성 연결
  - WebFlux gateway의 상관관계 보강

## 4. 판단 기준

| 질문 | 답 |
| --- | --- |
| `nginx` 로그와 앱 로그를 같이 적재할 수 있는가 | 가능 |
| `requestId` 기준으로 한 요청 흐름을 묶을 수 있는가 | 가능 |
| `traceId` 기준 완전 통합이 가능한가 | 조건부 가능 |
| `Micrometer`만으로 `nginx`까지 해결되는가 | 불가 |
| `OpenTelemetry Logs Data Model`로 수렴 가능한가 | 가능 |

핵심은 아래 두 가지다.

- `requestId`는 ingress부터 서비스까지 가장 빨리 통합할 수 있는 키다.
- `traceId`는 tracing 적용 범위가 넓어질수록 가치가 커진다.

## 5. 권장 구조

권장 구조는 아래와 같다.

`edge-nginx(JSON access log) -> stdout -> Grafana Alloy -> Loki`

`Spring services(JSON application log) -> stdout -> Grafana Alloy -> Loki`

보조 원칙:

- 공통 키는 `requestId`, `traceId`, `spanId`
- Loki label은 `service_name`, `env`, `log_type`, `level` 정도만 사용
- `requestId`, `traceId`, `userId`, `path`는 label이 아니라 payload에 둔다

## 6. 선택지 비교

### 선택지 A. JSON 로그만 통일

- 장점: 가장 빠름
- 단점: 표준성이 약함

### 선택지 B. 처음부터 OTel 완전 적용

- 장점: 표준성이 강함
- 단점: 초기 난이도가 높음

### 선택지 C. 하이브리드

- JSON으로 먼저 통일
- 상관관계 키는 OTel / tracing 기준으로 설계
- 수집기에서 OTel 모델로 정규화 가능하게 설계

현재 프로젝트에는 `선택지 C`가 가장 적합하다.

## 7. 단계별 추진

### 단계 1. 포맷 통일

- `edge-nginx` access log를 JSON으로 전환
- 공통 `logback-spring.xml` 도입
- 모든 서비스 로그를 stdout JSON으로 출력

### 단계 2. 상관관계 키 통일

- `requestId` 생성 정책 확정
- ingress / gateway / service 전파
- 로그 payload에 `requestId` 포함

### 단계 3. tracing 확대

- `libs:config:tracing`를 서비스와 gateway에 공통 적용
- Servlet 서비스에서 `traceId`, `spanId` 활용
- WebFlux gateway 보강

### 단계 4. OTel 정규화

- Alloy 또는 OTel Collector에서 OTel LogRecord로 정규화
- Loki OTLP 수신 여부 결정

## 8. 리스크

- `client-gateway`는 WebFlux라 Servlet 방식과 동일하지 않다.
- tracing 모듈은 존재하지만 아직 전 서비스에 자동 적용된 상태는 아니다.
- Loki label cardinality를 잘못 잡으면 운영이 어려워진다.
- PII 마스킹 정책 없이 로그를 늘리면 보안 리스크가 생긴다.

## 9. 최종 권장안

이 프로젝트의 현실적인 순서는 아래와 같다.

1. `JSON 로그 + requestId + Alloy + Loki`
2. `Micrometer Tracing 확대 + traceId`
3. `OpenTelemetry Logs Data Model 정규화`

즉, 처음부터 전부 다시 만드는 것보다, 공통 포맷과 공통 상관관계 키로 먼저 운영 가시성을 확보한 뒤, tracing과 OTel 표준으로 점진 수렴하는 방식이 가장 적절하다.
