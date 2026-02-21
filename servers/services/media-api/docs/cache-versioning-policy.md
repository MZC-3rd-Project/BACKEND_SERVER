# CloudFront Cache / Versioning Strategy

## Cache-Control 기준

### 썸네일(변경 빈도 낮음)
- 권장: `public, max-age=31536000, immutable`
- 이유: 파일 교체 대신 경로 버저닝으로 새 키를 발급하면 강한 캐시가 유리

### 원본/상세 이미지(변경 가능성 존재)
- 권장: `public, max-age=604800, stale-while-revalidate=60`
- 이유: 과도한 재검증을 줄이면서도 변경 반영 지연을 제한

## 버저닝 전략

### 원칙
- 동일 논리 자산이 변경되면 **동일 키 재사용 금지**
- 새 object key를 발급해 캐시를 자연 전환한다.

### 현재 구현 포인트
- 업로드 키가 `UUID + 원본 파일명` 구조이므로 기본적으로 경로 버저닝 효과를 가진다.
- invalidation 없이도 신규 키 조회 시 최신 자산을 즉시 사용할 수 있다.

## Invalidation 최소화 원칙
- 운영 중 대량 invalidation은 비용/지연이 커서 기본 정책으로 두지 않는다.
- 예외적으로 동일 키를 강제로 대체한 경우에만 좁은 path 단위 invalidation을 수행한다.

## 캐시 반영 검증 방법
1. 동일 리소스 재조회 시 `Age` 헤더 증가 확인(캐시 히트)
2. 신규 키 발급 후 조회 시 즉시 최신 파일 확인(캐시 미스 후 저장)
3. 필요 시 `curl -I https://<cloudfront-domain>/<key>`로 `Cache-Control` 응답 확인
