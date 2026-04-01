# Gateway 발표 자료

## 사용할 장표

- [donmoa-gateway-slide-1-overview.puml](./donmoa-gateway-slide-1-overview.puml)
- [donmoa-gateway-slide-2-session-relay.puml](./donmoa-gateway-slide-2-session-relay.puml)
- [donmoa-gateway-slide-3-client-bff.puml](./donmoa-gateway-slide-3-client-bff.puml)
- [donmoa-gateway-slide-4-business-bff.puml](./donmoa-gateway-slide-4-business-bff.puml)

## Gateway 핵심 특징 요약

- 두 개의 게이트웨이가 있다: `client-gateway`, `business-gateway`
- 단순 프록시가 아니라 BFF 계층 역할을 함께 수행한다
- 세션 인증을 downstream에서 검증 가능한 내부 헤더로 relay한다
- 읽기 BFF와 쓰기 BFF의 책임이 다르다
- 클라이언트가 여러 downstream을 직접 호출하지 않도록 응답 조합과 오케스트레이션을 맡는다

## 슬라이드 진행 순서에 맞춘 발표 대사 상세 버전

### 1번 슬라이드 보여주고

첫 번째 슬라이드는 Gateway의 전체 역할을 보여주는 장표입니다.
이 프로젝트의 Gateway는 단순히 요청을 다른 서비스로 전달하는 프록시가 아니라,
Edge 진입점과 BFF 역할을 같이 수행합니다.

왼쪽의 클라이언트 요청이 들어오면,
한쪽은 `client-gateway`, 다른 한쪽은 `business-gateway`로 들어갑니다.
`client-gateway`는 `CatalogBffService`, `CommerceReadBffService`, `SearchMediaBffService`처럼
읽기와 상세 조회 중심의 BFF 조합을 담당합니다.
반면 `business-gateway`는 `BusinessProductMediaBffService`, `BusinessOrderQueryBffService`, `BusinessSellerDashboardBffService`처럼
셀러나 관리자 관점의 업무 흐름을 묶는 BFF 역할이 더 강합니다.

즉, 이 장표의 핵심은
"Gateway는 단순 라우터가 아니라, 클라이언트 종류와 사용 맥락에 맞는 BFF 계층까지 포함한다"입니다.

### 2번 슬라이드 보여주고

두 번째 슬라이드는 보안과 세션 relay 역할입니다.
Gateway 쪽에서는 `GatewaySessionCookieAuthenticationWebFilter`가 세션 쿠키 기반 인증 컨텍스트를 만들고,
`GatewaySessionPrincipalResolver`가 실제로 userId와 roles를 해석합니다.

그 다음 `SessionHeaderRelayGlobalFilter`가 이 정보를
`X-Gateway-Context`, `X-User-Id`, `X-User-Roles` 같은 내부 헤더로 바꿔 downstream으로 전달합니다.
즉, Gateway는 세션 인증 결과를 downstream 서비스가 이해할 수 있는 내부 헤더 형식으로 relay하는 역할을 합니다.

그리고 downstream에서는 `GatewaySecurityValidationFilter`와 `GatewayRequestVerifier`가 이 헤더를 공통으로 검증합니다.
그래서 각 서비스가 인증 검증을 다시 구현하지 않아도 됩니다.

이 장표의 핵심은
"Gateway가 인증 결과를 전달하고, downstream은 공통 보안 모듈로 검증한다"입니다.

### 3번 슬라이드 보여주고

세 번째 슬라이드는 `client-gateway`의 읽기 BFF 역할을 보여줍니다.
대표 예시로 `CatalogBffService`를 보면,
이 서비스는 단순히 Search Service 응답을 전달하지 않습니다.
먼저 검색 결과를 받고,
그 다음 Media Service에서 썸네일 URL을 조회하고,
`SearchThumbnailFallbackEnricher`와 `CatalogResponseMapper`를 통해
fallback과 응답 매핑까지 수행합니다.

즉, Gateway는 여러 downstream 응답을 조합해서
클라이언트가 바로 쓰기 쉬운 하나의 응답으로 바꿔 줍니다.
클라이언트는 Search 서비스, Media 서비스, fallback 로직을 각각 몰라도 되고,
정리된 BFF 응답만 받으면 됩니다.

그래서 이 장표의 핵심은
"client-gateway는 읽기 응답을 통합하고 보강해서 클라이언트 친화적으로 바꾼다"입니다.

### 4번 슬라이드 보여주고

네 번째 슬라이드는 `business-gateway`의 쓰기 BFF 역할입니다.
대표 예시로 `BusinessProductMediaBffService`를 보면,
이 서비스는 상품이나 굿즈, 공연을 생성하거나 수정할 때
단순히 Product Service 한 번만 호출하지 않습니다.

먼저 상품을 생성하거나 수정하고,
그 다음 Media Service로 이미지 추가, 삭제, 재정렬 같은 연산을 순차적으로 수행하고,
마지막에는 다시 상세를 재조회해서 Media URL까지 보강합니다.

즉, 클라이언트는 한 번의 BFF 요청만 보내지만,
Gateway 내부에서는 여러 downstream 호출을 오케스트레이션하는 구조입니다.
그래서 프론트엔드가 복잡한 순차 호출을 직접 짜지 않아도 되고,
비즈니스 흐름이 Gateway에 묶여서 일관되게 관리됩니다.

이 장표의 핵심은
"business-gateway는 쓰기 업무 흐름을 하나의 BFF 요청으로 묶어 준다"입니다.

### 마무리 멘트

정리하면,
이 프로젝트의 Gateway는 단순한 reverse proxy가 아니라
라우팅, 세션/보안 relay, 읽기 BFF 응답 조합, 쓰기 BFF 오케스트레이션을 함께 담당하는 계층입니다.

`client-gateway`는 읽기 중심 경험을 정리해 주고,
`business-gateway`는 셀러/관리자 업무 흐름을 하나의 호출로 묶어 줍니다.
그리고 공통적으로는 세션 인증 결과를 downstream 보안 모듈이 검증할 수 있는 형태로 relay합니다.

즉, Gateway를 둔 이유는 단순한 경로 분기가 아니라,
클라이언트 복잡도를 줄이고,
보안 정책을 공통화하고,
도메인별 응답 조합과 업무 흐름을 중앙에서 관리하기 위해서라고 보시면 됩니다.

## 짧은 발표 버전

이 프로젝트의 Gateway는 단순 프록시가 아니라 BFF 계층입니다.
`client-gateway`는 읽기 응답 조합과 보강을 맡고,
`business-gateway`는 쓰기 흐름 오케스트레이션을 맡습니다.
또 세션 인증 결과를 내부 헤더로 relay해서 downstream 서비스가 공통 보안 모듈로 검증할 수 있게 합니다.

## 발표자 메모

- 1번 슬라이드는 "역할 개요"
- 2번 슬라이드는 "보안과 세션 relay"
- 3번 슬라이드는 "읽기 BFF"
- 4번 슬라이드는 "쓰기 BFF"
- 반복 메시지는 "Gateway는 단순 프록시가 아니라 BFF + 보안 relay 계층"으로 잡으면 된다
