# Media Cleanup Policy

## 목적
- 미확정(`PENDING_UPLOAD`) 상태로 남은 업로드를 주기적으로 정리해 스토리지 누수를 방지한다.
- DB 상태와 S3 객체 사이의 불일치 시나리오를 운영 중에 안전하게 흡수한다.

## TTL 기준
- 기준 컬럼: `media_files.upload_token_expires_at`
- 만료 조건: `status = PENDING_UPLOAD` 이고 `upload_token_expires_at < now`
- 기본 배치 크기: `100` (`media.cleanup.batch-size`)

## 처리 절차
1. 만료 대상을 배치 단위로 조회한다.
2. DB 상태를 `EXPIRED`로 전이한다.
3. `media.cleanup.delete-object-enabled=true` 인 경우에만 S3 `DeleteObject`를 시도한다.
4. 결과를 로그로 남긴다.
   - `expired`: 만료 전이 건수
   - `deletedObjects`: S3 삭제 성공 건수
   - `deleteFailed`: S3 삭제 실패 건수

## DB/S3 불일치 대응
- DB에만 있고 S3 객체가 없는 경우:
  - 상태 전이만 수행하고, 삭제 호출 실패는 경고 로그로 기록한다.
- S3에만 있고 DB가 없는 경우:
  - 애플리케이션 스케줄러가 찾을 수 없으므로 S3 Lifecycle 정책으로 정리한다.

## S3 Lifecycle vs 스케줄러 역할 분리
- 스케줄러:
  - DB 상태 정합성 유지 (`PENDING_UPLOAD -> EXPIRED`)
  - 필요한 경우 선택적으로 객체 삭제 시도
- S3 Lifecycle:
  - DB와 무관한 orphan/raw 객체 정리의 최종 안전망
  - 운영 환경에서 대량 정리에 유리

## 권장 운영값
- 개발/스테이징:
  - `media.cleanup.delete-object-enabled=true` (빠른 검증 목적)
- 운영:
  - 초기엔 `false`로 두고 Lifecycle 정책을 주 정리 수단으로 운영
  - 필요 시 점진적으로 `true` 전환
