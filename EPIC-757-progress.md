# Seller Ops Dashboard Epic Progress (Issue #757)

이 문서는 `#757` 에픽 기준으로 현재 구현 상태를 브랜치 단위로 모아 검토하기 위한 진행 현황이다.

## 1) Branch Breakdown

1. `task/762-analytics-dashboard-bootstrap`
- Commit: `7e20502`
- Scope:
  - `analytics-dashboard` 모듈 추가
  - `settings.gradle.kts` 등록
  - `application.yml`, `application-test.yml` 추가
  - `@SpringBootTest` 컨텍스트 로딩 테스트 추가
- Verification:
  - `./gradlew :servers:services:analytics-dashboard:test` 통과

2. `task/765-dashboard-query-mode-validation`
- Commit: `088474c`
- Scope:
  - `DAILY|MONTHLY|RANGE` 조회 모드 파서
  - `date/yearMonth/from/to/bucket/timezone` 검증
  - RANGE 최대 93일 제약
  - `AnalyticsDashboardErrorCode` 도입
- Verification:
  - `SellerDashboardOverviewQueryTest` 추가 및 통과

3. `task/764-dashboard-overview-api-bff`
- Commit: `b386158`
- Scope:
  - analytics 내부 API:
    - `GET /internal/v1/analytics/sellers/{sellerId}/dashboard/overview`
  - BFF API:
    - `GET /bff/v1/seller/dashboard/overview`
  - 응답 기본 구조:
    - `asOf`, `lagStatus`, `partial`, `sales/item/search`, `series`, `extensions`
  - gateway 설정:
    - `app.service.analytics-dashboard-url`
- Verification:
  - `./gradlew :servers:services:analytics-dashboard:test :servers:gateways:client-gateway:compileJava` 통과

4. `task/763-analytics-schema-entities`
- Commit: `c5987c2`
- Scope:
  - raw/dim/agg 엔티티 추가:
    - `analytics_raw_sales_event`
    - `analytics_raw_search_event`
    - `analytics_dim_item_snapshot`
    - `analytics_agg_seller_kpi_minute`
    - `analytics_agg_seller_kpi_daily`
    - `analytics_agg_seller_kpi_monthly`
  - 대응 JPA Repository 추가
- Verification:
  - `./gradlew :servers:services:analytics-dashboard:test` 통과

5. `task/771-dashboard-storeid-context` (현재 작업 브랜치)
- Scope (진행 중):
  - `storeId 중심 + sellerId 행위자` 모델 확장
  - 공통 헤더 상수 `X-Store-Id` 추가 (`libs/contracts/http`)
  - BFF -> analytics 호출을 store 경로 기반으로 확장:
    - `GET /internal/v1/analytics/stores/{storeId}/dashboard/overview`
  - analytics raw/dim/agg 엔티티에 `storeId` 컬럼 추가
  - agg unique index를 `bucket + store_id`로 전환
  - store 기반 repository 메서드 추가
- Verification:
  - `./gradlew :servers:services:analytics-dashboard:test :servers:gateways:client-gateway:compileJava` 통과

6. `epic/757-seller-ops-dashboard-mvp` (통합 진행)
- Scope (진행 중):
  - analytics ingest 컨슈머 추가:
    - `AnalyticsItemEventConsumer` (`item-events`)
    - `AnalyticsSalesEventConsumer` (`sales-events`)
    - `AnalyticsSearchEventConsumer` (`search-events`)
  - ingest 서비스 추가:
    - `AnalyticsEventIngestService`
    - item 이벤트 -> `analytics_dim_item_snapshot` upsert
    - sales/search 이벤트 -> raw 테이블 적재
  - 컨슈머/ingest 단위 테스트 추가
- Verification:
  - `./gradlew :servers:services:analytics-dashboard:test` 통과
  - `./gradlew :servers:gateways:client-gateway:compileJava` 통과

## 2) Common Module Reuse Status

다음 공통 모듈을 그대로 재사용했다.

1. `libs/api/response`
- 공통 `ApiResponse` 규격 유지

2. `libs/api/exception-handler`
- `BusinessException` + 공통 예외 처리 체계 유지

3. `libs/security/security-starter`
- 내부 API 보호 기본 체계 유지

4. `libs/contracts/http`
- `HttpHeaderNames` 재사용
- `STORE_ID` 상수 추가로 헤더 문자열 하드코딩 제거

## 3) Epic Review Checklist (with you)

검토 시 아래 순서로 확인 권장:

1. 경계/책임
- BFF는 조합/파싱만, 도메인/분석 책임 분리 유지 여부

2. 계약
- 공통 응답 포맷(`success/data/error/timestamp`) 유지 여부
- `DAILY/MONTHLY/RANGE` 입력 검증 규칙 일치 여부

3. 멀티 스토어 확장
- `storeId`를 집계 키로 보는 설계가 현재 운영 모델과 맞는지
- `sellerId`를 행위자/권한 컨텍스트로 유지하는 방향 합의 여부

4. 남은 구현
- 이벤트 ingest(sales/search/item)
- 실집계(minute/daily/monthly 계산)
- 지연/partial/fallback 정책
- E2E 시나리오

## 4) Next Implementation Queue

1. `task/772-analytics-ingest-consumers`
- sales/search/item 이벤트 컨슈머 + raw/dim 적재
- 현재 epic 브랜치에서 1차 구현 완료, 추후 이벤트 계약 확장(`sales seller/store 필드`, `search event producer`) 보강 필요

2. `task/773-dashboard-kpi-aggregation`
- minute/daily/monthly 집계 계산 로직

3. `task/774-dashboard-overview-read-model`
- repository 기반 실데이터 조회로 현재 empty 응답 대체

4. `task/775-dashboard-e2e`
- BFF end-to-end 검증 스크립트 및 테스트
