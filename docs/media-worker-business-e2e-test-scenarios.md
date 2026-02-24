# Media Worker Business E2E Test Scenarios

## 1. 문서 목적

이 문서는 Media Worker를 "기술 컴포넌트"가 아니라 "비즈니스 품질 보장 장치"로 검증하기 위한 E2E 시나리오를 정리한다.
검증 축은 4가지다.

- 판매/수정 쓰기 흐름 성공
- 구매 조회 연속성(장애/지연 포함)
- 운영 복구 가능성(replay/backfill)
- 도메인 확장 가능성(상품 외 유저프로필 등)

## 2. 공통 사전조건

- services: `media-api`, `media-worker`, `product`, `search`, `client-gateway`
- infra: `postgres`, `kafka`, `elasticsearch`, `S3/CloudFront`(또는 동등 환경)
- 기본 계약:
  - 도메인 정본은 `mediaId`
  - URL 생성 책임은 Media
  - Search는 `thumbnailMediaId` + `thumbnailUrlSnapshot` 보조 저장
  - Gateway는 snapshot 누락 시 batch fallback

## 3. 합격 기준(비즈니스 관점)

- 이미지 처리 실패가 있어도 핵심 조회 응답(검색/상세)이 "빈 화면"으로 붕괴하지 않는다.
- 파생 완료 후에는 derived WebP가 우선 노출된다.
- FAILED/DLQ가 발생해도 운영 API로 복구 가능하다.
- 동시 수정/이벤트 역전에서도 "최종 사용자 의도"가 유지된다.

## 4. 시나리오 목록

| ID | 우선순위 | 시나리오 | 기대 비즈니스 결과 | 자동화 매핑 |
|---|---|---|---|---|
| MW-BIZ-001 | P0 | 정상 경로(업로드→확정→파생→상품 노출) | 판매자가 등록한 썸네일이 정상 노출 | `search_enricher_dlq_verify.sh` |
| MW-BIZ-002 | P0 | Worker 지연/중단 시 조회 연속성 | 구매자는 이미지를 계속 볼 수 있음(fallback/placeholder) | `gateway_search_fallback_e2e_verify.sh` |
| MW-BIZ-003 | P0 | Media 일시 장애 후 자동 회복 | 장애 해소 후 자동 보강 완료 | `search_enricher_dlq_verify.sh` |
| MW-BIZ-004 | P0 | FAILED + DLQ + replay | 운영자가 실패 건을 수동 복구 가능 | `search_enricher_dlq_verify.sh` |
| MW-BIZ-005 | P0 | backfill 재생성 | 누락 파생 건을 범위 단위로 복구 가능 | `search_enricher_dlq_verify.sh` |
| MW-BIZ-006 | P0 | 연속 수정/이벤트 역전 | 최종 썸네일 의도 보장(구값 역전 금지) | `gateway_media_bff_e2e_verify.sh` |
| MW-BIZ-007 | P0 | 이미지 삭제/해제 | 삭제 후 stale 노출 없음 | `gateway_media_bff_e2e_verify.sh` |
| MW-BIZ-008 | P1 | 캐시 stale 회복 | 보강 성공 후 조회 최신화 | `search_enricher_dlq_verify.sh` |
| MW-BIZ-009 | P1 | raw->webp 전환 검증 | 초기 raw 노출 후 최종 derived 전환 | `product_thumbnail_webp_transition_verify.sh` |
| MW-BIZ-010 | P1 | 멀티 인스턴스 동시성 | 중복 처리 없이 단일 정답 유지 | `product_media_resilience_verify.sh` |
| MW-BIZ-011 | P2 | 장시간 장애 리허설 | 운영 절차(runbook)로 복구 성공 | 수동 + 스크립트 |
| MW-BIZ-012 | P2 | 장시간 고부하 soak | 처리량 증가 시 품질/지연 안정 | 별도 부하 스크립트 |

## 5. 상세 시나리오(Given/When/Then)

### MW-BIZ-001 정상 경로

- Given:
  - 판매자가 이미지 업로드를 완료했고 `confirm` 성공
- When:
  - 상품 생성/수정 요청에 `thumbnailMediaId`를 포함
- Then:
  - `media_derivative_tasks`는 `COMPLETED`
  - `media_derivatives.object_key`는 `/derived/` + `.webp`
  - 상품 상세/검색 응답에 썸네일 URL이 노출
- 증거 수집:
  - API: product detail, search query
  - DB: `media_derivative_tasks`, `media_derivatives`

### MW-BIZ-002 조회 연속성(Degraded Mode)

- Given:
  - Worker 또는 Media에 장애/지연 주입
- When:
  - 구매자가 검색/상세 조회
- Then:
  - 5xx 없이 fallback 또는 placeholder 응답
  - 사용자 관점에서 목록/상세가 붕괴하지 않음
- 증거 수집:
  - Gateway 응답 코드/본문
  - fallback hit 로그/지표

### MW-BIZ-003 자동 회복

- Given:
  - 보강 시점에 media-api를 일시 중단
- When:
  - media-api를 재기동
- Then:
  - retryCount 증가 후 최종 `COMPLETED`
  - snapshot 보강 완료
- 증거 수집:
  - `search_thumbnail_enrichment_tasks.retry_count`
  - 최종 검색 응답의 thumbnail URL

### MW-BIZ-004 FAILED + DLQ + replay

- Given:
  - task가 `FAILED` 상태로 존재
- When:
  - `/internal/v1/media-worker/tasks/{taskId}/replay` 실행
- Then:
  - `queued=true`
  - task가 `FAILED`에서 이탈
- 증거 수집:
  - ops API 응답
  - `media_derivative_tasks.status` 전이

### MW-BIZ-005 backfill

- Given:
  - 특정 mediaId 범위에 누락/재생성 대상 존재
- When:
  - `/internal/v1/media-worker/tasks/backfill` 실행
- Then:
  - `scannedCount/queuedCount/existingCount`가 일관
  - 중복 task 생성 없음
- 증거 수집:
  - ops API 응답
  - task unique key 충돌 여부

### MW-BIZ-006 연속 수정/이벤트 역전

- Given:
  - 동일 상품에 대해 썸네일 A->B를 짧은 간격으로 변경
- When:
  - 이벤트/보강 응답이 역순 도착
- Then:
  - 최종 노출은 B(최신 의도)
- 증거 수집:
  - Product `thumbnailMediaId`
  - ES `thumbnailMediaId`, `thumbnailUrlSnapshot`

### MW-BIZ-007 삭제/해제

- Given:
  - 상품 이미지 전체 해제 또는 썸네일 삭제
- When:
  - links sync + 조회 수행
- Then:
  - stale 이미지 재노출 없음
  - media/product 링크 정합성 유지
- 증거 수집:
  - `item_images`, `media_links`
  - 상세 응답 이미지 필드

### MW-BIZ-008 캐시 stale 회복

- Given:
  - 보강 완료 직후 캐시 hit 상태
- When:
  - 동일 질의 반복
- Then:
  - 오래된 URL 고착 없이 최신 반영
- 증거 수집:
  - 캐시 무효화 로그
  - 응답 URL 변경 시점

### MW-BIZ-009 raw->webp 전환

- Given:
  - confirm 직후 worker 완료 전
- When:
  - 조회 요청
- Then:
  - 초기 raw(또는 fallback) -> 최종 derived webp로 전환
- 증거 수집:
  - object_key before/after

### MW-BIZ-010 멀티 인스턴스 동시성

- Given:
  - worker/search 멀티 인스턴스 유사 환경
- When:
  - 동시 수정/동시 소비
- Then:
  - 중복 성공/정합성 깨짐 없음
- 증거 수집:
  - unique key 충돌 카운트
  - 최종 썸네일 단일성

### MW-BIZ-011 장시간 장애 리허설

- Given:
  - 장애를 30분 이상 유지
- When:
  - runbook 절차대로 복구 수행
- Then:
  - failed/dlq backlog를 소진 가능
- 증거 수집:
  - 시간대별 summary 스냅샷

### MW-BIZ-012 고부하 soak

- Given:
  - 지속 업로드/수정 트래픽
- When:
  - 수 시간 실행
- Then:
  - 지연/실패 지표가 임계치 내 유지
- 증거 수집:
  - latency P95, failed rate, dlq growth

## 6. 신규 도메인 온보딩 검증(상품 외 공통)

### MW-DOM-001 신규 도메인 기본 계약

- 목표: 새로운 도메인(예: User Profile, Store Banner)이 들어와도 동일 프로세스로 동작하는지 확인
- 체크리스트:
  1. 도메인 DB는 URL이 아닌 `mediaId` 정본 저장
  2. 쓰기 시점에 `links/sync` 또는 동등한 링크 동기화 경로 보유
  3. 조회 시 URL은 Media API에서 해석
  4. 검색/목록이 있다면 snapshot + fallback 전략 적용
  5. 삭제/순서/대표 이미지 규칙을 단일 경로로 처리

### MW-DOM-002 User Profile 특화 시나리오

- Given:
  - 유저가 프로필 이미지를 업로드/변경
- When:
  - 연속 변경(A->B), 장애 주입, 복구 실행
- Then:
  - 최종 노출은 B
  - 장애 중에도 기본 프로필 표시(placeholder/fallback)
  - 복구 후 snapshot/캐시 최신화

## 7. 배포 전 최종 게이트

- [ ] P0 시나리오(MW-BIZ-001~007) 모두 통과
- [ ] Ops API(replay/backfill) 복구 경로 통과
- [ ] fallback 비율이 임계치 내
- [ ] DLQ 증가 추세 없음(또는 원인/대응책 명확)
- [ ] 신규 도메인 온보딩 체크리스트 충족

## 8. 결론

현재 구조는 "도메인 독립적인 이미지 처리 플랫폼"으로 확장 가능한 형태다.
단, 신규 도메인이 아래 계약을 반드시 지켜야 한다.

- 정본은 `mediaId`
- URL 생성 책임은 Media
- 링크 상태는 단일 쓰기 경로
- 조회 가용성은 snapshot+fallback

즉, 구조는 준비되어 있고, 신규 도메인은 이 계약에 맞춰 온보딩해야 안전하게 확장된다.
