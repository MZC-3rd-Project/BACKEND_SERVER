# DonMoa 로깅 표준

작성일: 2026-03-23

## 1. 목적

이 문서는 `edge-nginx`와 애플리케이션 로그를 한 기준으로 수집하고 조회하기 위한 팀 공통 로깅 규칙을 정의한다.

## 2. 기본 원칙

- 모든 런타임 로그는 JSON으로 출력한다.
- 모든 애플리케이션 로그는 stdout으로 출력한다.
- 모든 요청 로그는 가능한 경우 `requestId`를 포함한다.
- tracing이 활성화된 서비스는 `traceId`, `spanId`를 포함한다.
- Loki label은 최소화하고, 고유값은 payload로 남긴다.

## 3. 필수 필드

애플리케이션 로그는 아래 필드를 기본으로 가져야 한다.

- `timestamp`
- `service.name`
- `deployment.environment`
- `log.type`
- `level`
- `logger`
- `thread`
- `message`
- `requestId`
- `traceId`
- `spanId`

`requestId`, `traceId`, `spanId`는 없는 경우 생략될 수 있다. 다만 `requestId`는 HTTP 요청 경로에서 최대한 유지한다.

## 4. 메시지 작성 규칙

- 메시지는 짧고 명확한 문장으로 작성한다.
- 결과와 원인을 같이 적는다.
- key-value 파라미터는 placeholder로 남긴다.
- 다중 행 로그를 피한다.
- 성공 로그는 남용하지 않는다.

권장 예시:

- `Order created. orderId={}`
- `Funding detail lookup failed. campaignId={}`
- `Email delivery skipped by user settings. userId={}, type={}`

비권장 예시:

- `성공`
- `여기까지 옴`
- `에러남`
- 원문 payload 전체 출력

## 5. 로그 레벨 규칙

- `DEBUG`: 재시도, fallback, enrichment skip, 캐시 재구성 같은 저수준 진단
- `INFO`: 상태 전이 완료, 배치 완료, 운영상 의미 있는 정상 이벤트
- `WARN`: 복구 가능 오류, fallback 발생, 외부 의존성 실패 후 대체 경로 진입
- `ERROR`: 요청 실패, 데이터 손실 가능성, 재시도 한계 초과, 운영자 개입 필요

## 6. 민감정보 규칙

아래 정보는 로그에 직접 남기지 않는다.

- 비밀번호
- 인증 토큰
- 주민번호, 전화번호, 이메일 원문
- 카드 정보
- 외부 시스템의 전체 응답 payload

## 7. Loki label 규칙

허용 label:

- `service_name`
- `env`
- `log_type`
- `level`

금지 label:

- `requestId`
- `traceId`
- `userId`
- `sessionId`
- `path`
- `clientIp`

## 8. Nginx 규칙

- access log는 JSON으로 출력한다.
- `request_id`, `status`, `method`, `uri`, `request_time`, `upstream_*`를 남긴다.
- 필요 시 `gzip_ratio`, `bytes_sent`, `body_bytes_sent`를 포함한다.

## 9. 애플리케이션 규칙

- 공통 `logback-spring.xml`을 사용한다.
- 공통 MDC 키는 `requestId`, `traceId`, `spanId`를 사용한다.
- 새 로그를 추가할 때는 business key를 1~3개만 남긴다.
- 예외는 message만 따로 문자열로 붙이지 말고 exception 인자로 전달한다.
