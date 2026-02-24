# Media Worker 운영 Runbook

## 1. 목적

이 문서는 Media Worker 장애/지연 상황에서 운영자가 실제로 확인하고 복구할 수 있는 절차를 정리한다.
핵심 대상은 아래 4가지다.

- 파생 처리 상태 요약 확인
- FAILED/DLQ 누적 확인
- FAILED 작업 수동 replay
- 범위 기반 backfill enqueue

## 2. 파생 처리 규칙(현재 구현 기준)

코드 기준:
- `MediaWorkerDerivativeProperties`
- `S3MediaDerivativeProcessor`

기본 규칙:
- 출력 포맷: `image/webp`
- 썸네일 최대 크기: `640x640` (비율 유지)
- 기본 품질: `0.82` (WebP writer 품질 82)
- objectKey: 원본 `.../raw/...` 기준 `.../derived/...` 경로로 변환

환경변수:
- `MEDIA_WORKER_DERIVATIVE_THUMBNAIL_MAX_WIDTH`
- `MEDIA_WORKER_DERIVATIVE_THUMBNAIL_MAX_HEIGHT`
- `MEDIA_WORKER_DERIVATIVE_WEBP_QUALITY`

## 3. 상태머신

- `PENDING`: 처리 대기
- `PROCESSING`: 워커 점유/처리중
- `COMPLETED`: 파생본 생성 완료
- `FAILED`: 재시도 한계 초과 또는 비재시도 오류

복구 전이:
- stale `PROCESSING` -> `PENDING`
- 운영 replay 시 `FAILED` -> `PENDING`

## 4. 운영 API

base URL 예시:
- `http://127.0.0.1:18395/internal/v1/media-worker`

### 4.1 상태 요약

```bash
curl -sS "http://127.0.0.1:18395/internal/v1/media-worker/tasks/summary" | jq
```

확인 포인트:
- `pendingCount`, `processingCount`, `completedCount`, `failedCount`, `dlqCount`

### 4.2 DLQ 최근 목록

```bash
curl -sS "http://127.0.0.1:18395/internal/v1/media-worker/tasks/dlq?size=20" | jq
```

확인 포인트:
- `items[].taskId`, `mediaId`, `errorCode`, `retryCount`, `createdAt`

### 4.3 FAILED 작업 replay

```bash
curl -sS -X POST \
  "http://127.0.0.1:18395/internal/v1/media-worker/tasks/{taskId}/replay" \
  -H "Content-Type: application/json" \
  -d '{"reason":"manual replay by operator"}' | jq
```

성공 조건:
- `data.queued == true`
- 이후 DB에서 해당 task가 `FAILED`가 아닌 상태로 이동

### 4.4 backfill enqueue

```bash
curl -sS -X POST \
  "http://127.0.0.1:18395/internal/v1/media-worker/tasks/backfill" \
  -H "Content-Type: application/json" \
  -d '{
    "fromMediaId": 1000,
    "toMediaId": 5000,
    "size": 200,
    "derivativeProfile": "THUMBNAIL_WEBP"
  }' | jq
```

응답 의미:
- `scannedCount`: 조회된 media_files 수
- `queuedCount`: 신규 enqueue 수
- `existingCount`: 기존 task 존재로 스킵된 수

## 5. 메트릭

`MediaWorkerMetricsService` 기준:
- `media.worker.tasks.success.total`
- `media.worker.tasks.failure.total`
- `media.worker.tasks.retry.scheduled.total`
- `media.worker.tasks.dlq.total`
- `media.worker.tasks.stale.recovered.total`
- `media.worker.tasks.processing.latency`

기본 운영 확인:
- 실패율 증가와 DLQ 증가가 동시에 나타나면 외부 의존성(S3/네트워크/포맷) 우선 점검
- `processing.latency` 증가와 `pendingCount` 증가가 동반되면 워커 처리량 또는 외부 I/O 병목 점검

## 6. 장애 대응 절차

1. 요약 조회로 `failedCount`, `dlqCount` 급증 여부 확인
2. DLQ 조회로 대표 `errorCode`/`errorMessage` 패턴 확인
3. 원인(외부 의존/포맷/권한) 조치
4. `replay` 또는 `backfill`로 복구 수행
5. 요약/메트릭 재확인

## 7. 검증 스크립트

### 7.1 Search Enricher + DLQ + Worker Ops E2E

아래 스크립트는 다음을 한 번에 검증한다.
- Product/Search 연계 썸네일 보강 정합성
- Search DLQ 적재
- Media Worker Ops API(summary/dlq/replay/backfill)

```bash
bash docker/scripts/search_enricher_dlq_verify.sh
```

성공 기준:
- 마지막 출력이 `failures=0`

## 8. 롤백 가이드

문제 발생 시 우선순위:
1. worker 스케줄/파생 기능 환경변수 off (`MEDIA_WORKER_DERIVATIVE_ENABLED=false`)
2. 기존 원본 URL fallback 정책 유지
3. 실패 원인 조치 후 replay/backfill 재개

주의:
- Product/Search는 `mediaId` 계약을 유지하므로, worker off 상태에서도 조회 경로는 원본 fallback으로 동작해야 한다.
