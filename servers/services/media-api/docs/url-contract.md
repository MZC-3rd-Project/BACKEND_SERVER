# Media URL Contract

## API 응답 계약
`UploadConfirmResponse`는 아래 URL 관련 필드를 포함한다.

- `mediaUrl`: 클라이언트가 조회에 사용하는 URL(CloudFront 도메인 기준)
- `urlAccessType`: `PUBLIC` | `SIGNED_URL` | `SIGNED_COOKIE`
- `urlExpiresAt`: `SIGNED_URL` 정책일 때 만료 시각(UTC), 그 외는 `null`
- `cacheControl`: 추천 캐시 정책 문자열

## 정책 규칙

### 접근 정책
- 기본값: `PUBLIC`
- 민감 자산(예: 사용자 원본, 제한 공개 영상): `SIGNED_URL` 또는 `SIGNED_COOKIE`
- 설정 위치: `media.url.access-type`

### 만료/재발급
- `SIGNED_URL`일 때 `media.url.signed-url-ttl-seconds`로 만료 시각을 계산한다.
- 만료 이후에는 재발급 API(추후 추가) 또는 재확정 조회 플로우로 URL을 갱신한다.

### 캐시
- 썸네일: `media.url.thumbnail-cache-control`
- 그 외: `media.url.default-cache-control`

## 클라이언트 가이드
1. `urlAccessType=PUBLIC`: 만료 처리 없이 일반 캐시 정책을 따른다.
2. `urlAccessType=SIGNED_URL`: `urlExpiresAt` 1~2분 전부터 선제 재요청한다.
3. `urlAccessType=SIGNED_COOKIE`: 앱 세션/쿠키 만료를 우선 확인하고, 필요 시 재로그인 또는 쿠키 재발급을 수행한다.

## 참고
- 현재 구현은 계약 필드와 정책 매핑을 제공한다.
- CloudFront 서명 키 기반의 실제 Signed URL/쿠키 발급은 후속 태스크에서 연결한다.
