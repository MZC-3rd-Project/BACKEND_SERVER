# Media Worker Epic/Story Plan

## 목적

이 문서는 Media Worker 보강 작업을 `mediaId` 중심 기존 흐름에 자연스럽게 안착시키기 위한 Epic/Story 분해안이다.

핵심 원칙:
- 식별자는 끝까지 `mediaId`를 사용한다.
- URL 생성/해석 책임은 Media 서비스에 유지한다.
- Product/Search/Gateway는 `mediaId`를 저장/전달하고 필요 시 Media에 URL 조회한다.
- Worker 도입은 비파괴 확장으로 진행한다(기존 API 계약 유지).

---

## Epic

### 제목
- `[EPIC] Media Worker 보강 및 mediaId 기반 무중단 이미지 파이프라인`

### 목표
- Confirm 이후 파생 처리(WebP/thumbnail)를 비동기로 표준화한다.
- 상태머신/멱등/재시도/DLQ를 갖춘 복구 가능한 워커 처리 체계를 구축한다.
- 조회 경로에서 `mediaId -> URL` 해석을 Media 단일 책임으로 유지한다.
- Search snapshot 보강과 Gateway fallback이 공존하는 읽기 정합성을 확립한다.
- 운영 메트릭/로그/알림으로 장애를 조기 감지하고 재처리 가능한 운영 절차를 마련한다.

### 하위 Story
- Story A: Media Worker 파생 처리 상태머신/멱등/재시도 도입
- Story B: mediaId 기반 URL 해석 일원화 및 조회 fallback 계약 정립
- Story C: Media Worker 운영 복구(관측성/DLQ/백필) 체계 구축

---

## Story A

### 제목
- `[STORY] Media Worker 파생 처리 상태머신/멱등/재시도 도입`

### 배경
- 현재 Upload Intent -> Confirm -> 링크 동기화 흐름은 안정화되었지만, 파생 이미지 생성의 처리 보장/복구 규칙이 분산되어 있다.
- Worker를 붙이더라도 Product/Search/Gateway 계약을 깨지 않으려면 파생 처리 자체가 멱등하고 재처리 가능해야 한다.

### 수용 조건
- [ ] WHEN media confirm이 완료되면 THEN 시스템 SHALL 파생 처리 task를 `PENDING`으로 생성한다.
- [ ] WHEN 워커가 task를 점유하면 THEN 시스템 SHALL 상태를 `PROCESSING`으로 전이한다.
- [ ] WHEN 파생 생성 성공 시 THEN 시스템 SHALL 상태를 `COMPLETED`로 전이하고 결과 메타를 저장한다.
- [ ] IF 재시도 한계를 초과하면 THEN 시스템 SHALL 상태를 `FAILED`로 전이하고 오류 정보를 보존한다.
- [ ] WHEN 동일 `mediaId + profile + version` task가 중복 유입되면 THEN 시스템 SHALL 멱등 처리로 중복 실행을 방지한다.

---

## Story B

### 제목
- `[STORY] mediaId 기반 URL 해석 일원화 및 조회 fallback 계약 정립`

### 배경
- 다운스트림 도메인에서 URL 생성 로직이 퍼지면 정책 변경(PUBLIC_DOMAIN/SIGNED_URL) 시 일관성이 깨진다.
- 기존 구조의 강점은 `mediaId`를 중심으로 Media가 URL 정책을 단일 책임으로 제공하는 점이다.
- Worker 도입 후에도 이 계약을 유지해야 기능/흐름 충돌 없이 안착된다.

### 수용 조건
- [ ] WHERE 다운스트림은 URL 생성 로직을 가지지 않고 `mediaId`만 저장/전달해야 한다.
- [ ] WHEN URL 조회 요청이 오면 THEN Media SHALL 파생본 우선, 원본 fallback 정책으로 URL을 반환한다.
- [ ] IF Search snapshot이 아직 없으면 THEN Gateway/BFF SHALL mediaId 기반 fallback 조회로 응답을 보완한다.
- [ ] WHEN 정책 모드가 변경되면 THEN Media SHALL 신규 조회부터 일관된 모드로 URL을 생성한다.
- [ ] WHEN 기존 조회 API를 호출하면 THEN 시스템 SHALL 하위 호환 계약을 유지해야 한다.

---

## Story C

### 제목
- `[STORY] Media Worker 운영 복구(관측성/DLQ/백필) 체계 구축`

### 배경
- 워커는 정상 시나리오보다 장애 시나리오에서 운영 품질이 결정된다.
- 재시도/DLQ/백필/알림 없이 도입하면 장시간 장애에서 처리 지연과 누락을 발견하기 어렵다.

### 수용 조건
- [ ] WHEN 파생 처리 실패가 발생하면 THEN 시스템 SHALL 백오프 재시도를 수행해야 한다.
- [ ] IF 실패가 지속되어 한계를 넘으면 THEN 시스템 SHALL DLQ/FAILED로 전이하고 원인을 조회 가능하게 저장해야 한다.
- [ ] WHEN 운영자가 리플레이를 실행하면 THEN 시스템 SHALL 기존 멱등 규칙으로 안전하게 재처리해야 한다.
- [ ] WHEN 백필을 실행하면 THEN 시스템 SHALL 대상 범위 기준으로 처리하고 성공/실패 집계를 제공해야 한다.
- [ ] WHEN 운영 중이라면 THEN 시스템 SHALL 성공률/실패율/지연시간/재시도/DLQ 지표와 핵심 구조 로그를 제공해야 한다.

