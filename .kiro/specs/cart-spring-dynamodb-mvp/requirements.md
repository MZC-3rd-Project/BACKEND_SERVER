# Requirements Document

## Introduction

현재 커머스 흐름은 `store -> product -> stock -> sales -> order`까지 연결되어 있고, 실제 구매 직전의 확정 로직은 `sales checkout`이 담당한다.

하지만 사용자가 구매 전에 상품을 모아두고 수량을 조정하고, 일부 상품만 선택해 checkout으로 넘기는 장바구니 경계는 아직 없다. 이 상태에서는 사용자가 여러 상품을 비교하거나 구매를 나중으로 미루는 기본 커머스 UX를 제공하기 어렵다.

이번 요구사항의 목표는 다음 기준을 고정하는 것이다.

- 장바구니 API는 Spring 서비스로 제공한다.
- 장바구니 저장소는 DynamoDB를 사용한다.
- 장바구니는 가격/재고/주문의 정본이 아니라 구매 전 상태 저장소로 동작한다.
- 장바구니는 `sales reserve` 요청을 만들 수 있는 최소 구매 의도 데이터를 보관한다.
- 장바구니는 완전한 read-model 대신 조회 UX에 필요한 가벼운 표시용 snapshot만 선택적으로 보관한다.
- 실제 가격 확정, 재고 예약, 주문 생성은 기존 `sales checkout` 경계를 재사용한다.

범위 포함:

- 사용자 장바구니 조회/추가/수정/삭제 API
- 장바구니 line item 데이터 모델 정의
- 선택 상품 기준 checkout handoff 규칙 정의
- 장바구니 만료/정리 정책 정의
- DynamoDB 기반 키 설계와 조회 패턴을 고려한 요구사항 정의

범위 제외:

- 포인트 적립/차감/정산 설계
- 쿠폰/프로모션 정산 로직
- 결제 완료 이후 상태 전이 상세 구현
- 주문 정본 저장 방식 변경

## Requirements

### Requirement 1 (R1)

**User Story:** As a 사용자, I want 상품을 장바구니에 담고 다시 조회할 수 있길 원한다, so that 구매 전에 상품을 모아두고 비교할 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 상품을 장바구니에 추가하면 THEN 시스템 SHALL 사용자 식별자 기준 장바구니 line item을 저장해야 한다.
2. WHEN 사용자가 장바구니를 조회하면 THEN 시스템 SHALL 현재 담긴 상품 목록과 선택 상태를 반환해야 한다.
3. IF 동일한 `itemId + referenceId + channelType + channelRefId` 조합이 다시 추가되면 THEN 시스템 SHALL 신규 row를 중복 생성하지 않고 기존 line item 수량을 갱신해야 한다.
4. WHEN 장바구니가 비어 있으면 THEN 시스템 SHALL 빈 목록을 정상 응답으로 반환해야 한다.

### Requirement 2 (R2)

**User Story:** As a 사용자, I want 장바구니에서 수량과 선택 상태를 바꿀 수 있길 원한다, so that 실제 구매 의도를 유연하게 조정할 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 장바구니 line item 수량을 변경하면 THEN 시스템 SHALL 해당 line item의 수량을 갱신해야 한다.
2. IF 사용자가 0 이하 수량을 요청하면 THEN 시스템 SHALL 유효성 오류를 반환해야 한다.
3. WHEN 사용자가 특정 line item을 선택 또는 해제하면 THEN 시스템 SHALL 선택 상태를 저장해야 한다.
4. WHEN 사용자가 line item 삭제를 요청하면 THEN 시스템 SHALL 해당 line item만 제거해야 한다.

### Requirement 3 (R3)

**User Story:** As a 플랫폼 엔지니어, I want 장바구니가 구매 전 상태 저장소로만 동작하길 원한다, so that 가격/재고/주문 정본과 책임이 섞이지 않는다.

#### Acceptance Criteria

1. WHEN 장바구니 line item을 저장하면 THEN 시스템 SHALL `itemId`, `referenceId`, `quantity`, `channelType`, `channelRefId`를 최소 식별 정보로 보관해야 한다.
2. WHEN 장바구니가 상품 정보를 함께 보관하면 THEN 시스템 SHALL 상품명, 썸네일, 가게명, 표시 가격 등은 조회 편의를 위한 light snapshot으로만 저장해야 한다.
3. IF 장바구니 snapshot과 실제 상품 정보가 달라지면 THEN 시스템 SHALL checkout 시점에 최신 가격/상태/재고를 재검증해야 한다.
4. WHERE 장바구니 데이터 모델은 시스템 SHALL 주문 정본 또는 재고 확정 정보를 source of truth로 취급하지 않아야 한다.

### Requirement 4 (R4)

**User Story:** As a 사용자, I want 장바구니에서 선택한 상품만 checkout으로 넘길 수 있길 원한다, so that 불필요한 상품은 남겨두고 일부만 구매할 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 checkout 시작을 요청하면 THEN 시스템 SHALL 선택된 line item만 대상으로 `sales checkout reserve` 요청 payload를 생성해야 한다.
2. WHEN checkout payload를 만들면 THEN 시스템 SHALL 현재 `sales reserve`가 요구하는 line item 구조를 그대로 사용해야 한다.
3. IF 선택된 line item이 하나도 없으면 THEN 시스템 SHALL checkout 시작을 거부해야 한다.
4. IF checkout 요청 중 일부 상품이 더 이상 판매 가능하지 않으면 THEN 시스템 SHALL 실패 사유를 반환하고 장바구니 원본은 유지해야 한다.

### Requirement 5 (R5)

**User Story:** As a 플랫폼 엔지니어, I want 장바구니가 다중 스토어와 다중 판매 형식을 지원하길 원한다, so that 현재 checkout 구조와 자연스럽게 연결된다.

#### Acceptance Criteria

1. WHEN 장바구니 line item을 저장하면 THEN 시스템 SHALL 서로 다른 `storeId`의 상품을 함께 보관할 수 있어야 한다.
2. WHEN 장바구니 line item을 저장하면 THEN 시스템 SHALL `NORMAL`, `FUNDING`, 이후 추가될 판매 문맥을 line item 단위로 표현할 수 있어야 한다.
3. IF 동일 상품이라도 `channelType` 또는 `channelRefId`가 다르면 THEN 시스템 SHALL 서로 다른 line item으로 구분해야 한다.
4. WHEN 장바구니를 checkout으로 넘기면 THEN 시스템 SHALL line item별 판매 문맥이 손실되지 않도록 전달해야 한다.

### Requirement 6 (R6)

**User Story:** As a 운영자, I want 장바구니 데이터가 무한정 쌓이지 않길 원한다, so that 세션성 데이터 저장 비용과 관리 복잡도를 통제할 수 있다.

#### Acceptance Criteria

1. WHEN 장바구니가 일정 기간 갱신되지 않으면 THEN 시스템 SHALL 만료 대상으로 간주해야 한다.
2. WHEN 만료 정책을 적용하면 THEN 시스템 SHALL 저장소 차원의 TTL 정리 또는 배치 정리를 지원해야 한다.
3. IF 만료 대상 장바구니를 사용자가 다시 조회하면 THEN 시스템 SHALL 만료된 line item을 제외한 결과를 반환하거나 빈 장바구니를 반환해야 한다.
4. WHEN 장바구니 메타데이터를 저장하면 THEN 시스템 SHALL 마지막 갱신 시각과 만료 기준 시각을 추적할 수 있어야 한다.

### Requirement 7 (R7)

**User Story:** As a 백엔드 개발자, I want 장바구니 조회/수정 API가 저장소 특성에 맞게 효율적으로 동작하길 원한다, so that 대량 사용자 환경에서도 낮은 지연으로 유지할 수 있다.

#### Acceptance Criteria

1. WHEN 장바구니 데이터 모델을 정의하면 THEN 시스템 SHALL 사용자 기준 단건 조회 패턴을 우선 최적화해야 한다.
2. WHEN 장바구니 목록을 조회하면 THEN 시스템 SHALL 전체 스캔 없이 사용자 기준 query 패턴으로 조회할 수 있어야 한다.
3. IF 저장소가 DynamoDB라면 THEN 시스템 SHALL 파티션 키와 정렬 키만으로 기본 장바구니 조회/병합/삭제가 가능해야 한다.
4. WHEN 장바구니 line item을 추가 또는 변경하면 THEN 시스템 SHALL 낙관적 충돌 제어 또는 조건부 쓰기 전략을 사용할 수 있어야 한다.

### Requirement 8 (R8)

**User Story:** As a 백엔드 개발자, I want 장바구니 API가 기존 commerce 서비스와 느슨하게 연결되길 원한다, so that 장바구니 장애가 checkout 정본 흐름 전체를 오염시키지 않는다.

#### Acceptance Criteria

1. WHEN 장바구니 조회 시 상품 snapshot 보강이 필요하면 THEN 시스템 SHALL 실패하더라도 핵심 식별 정보 기반 line item 조회는 가능해야 한다.
2. IF 외부 상품 정보 조회가 실패하면 THEN 시스템 SHALL 저장된 snapshot 또는 최소 식별 정보로 fallback할 수 있어야 한다.
3. WHEN checkout handoff를 수행하면 THEN 시스템 SHALL `sales`가 처리 가능한 최소 payload만 넘기고 주문 상태 관리는 계속 `sales`가 담당해야 한다.
4. WHERE 장바구니 서비스는 시스템 SHALL 재고 예약, 주문 생성, 결제 완료 상태 저장 책임을 가지지 않아야 한다.

### Requirement 9 (R9)

**User Story:** As a QA/운영자, I want 장바구니와 checkout 연계 흐름을 재현 가능하게 검증하길 원한다, so that 사용자 구매 전환 경로의 회귀를 빠르게 잡을 수 있다.

#### Acceptance Criteria

1. WHEN 장바구니 기능을 구현하면 THEN 시스템 SHALL 단위 테스트로 add/update/delete 규칙을 검증해야 한다.
2. WHEN 장바구니에서 checkout으로 넘어가는 흐름을 구현하면 THEN 시스템 SHALL integration 또는 e2e 테스트로 `cart -> sales reserve -> quote -> submit` 경로를 검증해야 한다.
3. IF 다중 스토어 또는 다중 판매 문맥 line item이 포함되면 THEN 시스템 SHALL 해당 조합이 checkout으로 정확히 전달되는지 검증해야 한다.
4. WHEN 만료 또는 비판매 상품 시나리오를 검증하면 THEN 시스템 SHALL 실패 응답과 장바구니 유지 정책을 함께 확인해야 한다.
