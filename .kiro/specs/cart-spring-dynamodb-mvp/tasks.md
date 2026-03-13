# Implementation Plan

## Tracking

- Spec: `cart-spring-dynamodb-mvp`
- Runtime: `Spring Boot + DynamoDB`
- Checkout integration target: `sales reserve`

## Tasks

### 1. Service Skeleton and Infrastructure

- [ ] `servers/services/cart` 모듈을 Gradle/settings 에 등록한다.
- [ ] cart 서비스 기본 Spring Boot 기동 구성을 추가한다.
- [ ] AWS SDK v2 DynamoDB Enhanced Client 설정을 추가한다.
- [ ] 환경별 DynamoDB table name, endpoint, credential profile 설정 구조를 정의한다.

### 2. Domain Model and DynamoDB Mapping

- [ ] `Cart`, `CartLine`, `CartLineIdentity` 도메인 모델을 정의한다.
- [ ] 구매 의도 필드와 light snapshot 필드를 나눠 cart line 모델에 반영한다.
- [ ] DynamoDB PK/SK 규칙과 TTL attribute 매핑 클래스를 정의한다.
- [ ] duplicate identity merge, quantity validation, selection 규칙 단위 테스트를 작성한다.

### 3. Repository and Persistence Layer

- [ ] userId 기준 장바구니 전체 조회 repository 를 구현한다.
- [ ] line item 단건 upsert/update/delete repository 를 구현한다.
- [ ] TTL attribute 저장과 조회 시 만료 필터링 정책을 구현한다.
- [ ] optimistic write 또는 conditional write 전략을 정하고 repository 테스트를 추가한다.

### 4. Snapshot Enrichment

- [ ] product internal query 기반 light snapshot enricher 를 구현한다.
- [ ] 필요 시 store/store-query 기반 storeName 보강 경로를 추가한다.
- [ ] enrichment 실패 시 최소 line item 저장으로 fallback 하는 정책을 구현한다.
- [ ] snapshot enrichment 성공/실패 테스트를 추가한다.

### 5. Cart Query and Command APIs

- [ ] `GET /api/v1/cart` 조회 API 를 구현한다.
- [ ] `POST /api/v1/cart/items` add/upsert API 를 구현한다.
- [ ] `PATCH /api/v1/cart/items` quantity 변경 API 를 구현한다.
- [ ] `PATCH /api/v1/cart/items/selection` 선택 상태 변경 API 를 구현한다.
- [ ] `DELETE /api/v1/cart/items` 삭제 API 를 구현한다.

### 6. Checkout Handoff to Sales

- [ ] selected line item 을 `CheckoutReserveRequest` 로 변환하는 도메인 로직을 구현한다.
- [ ] `POST /api/v1/cart/checkout/reservations` API 를 구현해 `sales reserve` 를 위임 호출한다.
- [ ] checkout 실패 시 cart 상태를 유지하는 정책을 구현한다.
- [ ] `cart -> sales reserve` integration 테스트를 추가한다.

### 7. Verification and E2E

- [ ] `:servers:services:cart:compileJava` 를 통과시킨다.
- [ ] cart 도메인 단위 테스트를 통과시킨다.
- [ ] repository/integration 테스트를 통과시킨다.
- [ ] `cart -> sales reserve -> quote -> submit` e2e 시나리오를 추가한다.
- [ ] cross-store, mixed sales channel, stale snapshot fallback 시나리오를 검증한다.
