# Module: `servers/gateways/client-gateway`

## 한눈에 보기
- 역할: 외부 요청 진입점 게이트웨이 모듈입니다.
- 사용 시점: 인증/라우팅/헤더 주입 정책을 적용할 때 사용합니다.

## 이 서비스가 맡는 책임
이 모듈은 특정 도메인의 API와 비즈니스 규칙을 처리하는 독립 서비스입니다.
컨트롤러는 요청/응답 변환에 집중하고, 핵심 규칙은 서비스 계층에서 처리하며,
데이터 저장/조회는 리포지토리로 분리해 변경 영향을 최소화합니다.

## 간단 예시
```text
./gradlew :servers:gateways:client-gateway:bootRun
```

## BFF 오케스트레이션 (WebFlux)
- `POST /bff/v1/products`
  - 내부적으로 `POST /api/products` -> (선택) `POST /api/items/{itemId}/images` -> `GET /api/products/{itemId}` 순서로 호출합니다.
- `PUT /bff/v1/products/{itemId}`
  - 내부적으로 `PUT /api/products/{itemId}` 이후 이미지 연산(`add/delete/reorder`)을 순차 호출하고 마지막에 상세를 재조회합니다.
- `POST /bff/v1/goods`, `PUT /bff/v1/goods/{itemId}`
  - 굿즈 생성/수정 + 이미지 연산을 상품과 동일한 패턴으로 처리합니다.
- `POST /bff/v1/performances`, `PUT /bff/v1/performances/{itemId}`
  - 공연 생성/수정 + 이미지 연산을 동일 패턴으로 처리합니다.
- `GET /bff/v1/items/{itemId}?type=PRODUCT|GOODS|PERFORMANCE`
  - 타입 기반 단건 조회를 BFF에서 통합 제공합니다.
- `GET /bff/v1/items?type=PRODUCT|GOODS|PERFORMANCE&cursor=&size=`
  - 타입 기반 목록 조회를 BFF에서 통합 제공합니다.
- 목적: 클라이언트가 상품+이미지 처리를 단일 BFF 호출 흐름으로 사용할 수 있게 통합.

## 로컬 E2E 검증
```text
./docker/scripts/gateway_jwt_header_verify.sh
./docker/scripts/gateway_session_trusted_auth_verify.sh
./docker/scripts/gateway_session_userid_itemsearch_e2e.sh
```
- 기본 포트: Gateway `18173`, Chat `18093`
- 환경 변수로 변경 가능: `GW_PORT`, `CHAT_PORT`, `LOG_DIR`
- `gateway_session_trusted_auth_verify.sh`는 `X-Gateway-Context`(HMAC 서명) + `X-Session-Id` + Redis `sid` 상태(`ACTIVE`) 조합으로 세션 인증 경로를 검증합니다.
- `gateway_session_userid_itemsearch_e2e.sh`는 세션 인증 상태에서 상품/채팅 도메인의 `X-User-Id` 전달과 아이템 노출 조건(상태/타입 필터) 동작을 검증합니다.

## 보안 헤더 서명
- `APP_SECURITY_CONTEXT_SIGNING_KEY`를 설정하면 Gateway가 사용자 컨텍스트 헤더를 HMAC으로 서명해 전달합니다.
- Chat 같은 소비 서비스도 같은 키를 사용해야 검증이 통과합니다.
- 미디어/비즈니스 쓰기 경로(`POST/PUT/PATCH/DELETE` on `/api/v1/media`, `/api/products`, `/api/goods`, `/api/performances`, `/api/items`, `/api/categories`, `/api/campaigns`, `/api/v1/sales`, `/api/v1/hot-deals`, `/api/v1/notifications`)와 BFF 쓰기 경로(`/bff/v1/**`)는 인증 Principal + `sid` 상태 검증이 필요합니다.
- 로컬/테스트에서는 `GATEWAY_SESSION_TRUSTED_HEADER_AUTH_ENABLED=true`일 때 `X-Gateway-Context` + `X-Session-Id`를 사용해 pre-auth를 구성할 수 있습니다(운영 비권장).
- 상품 조회(`GET`)는 익명 접근을 허용하되, 클라이언트가 보낸 `X-User-*`/`X-Gateway-*` 스푸핑 헤더는 Gateway가 제거합니다.

## BFF 인증 스켈레톤
- `bff-auth` 프로필이 활성화될 때 OAuth2 BFF 보안체인/TokenRelay 라우팅이 켜집니다.
- 예시: `SPRING_PROFILES_ACTIVE=bff-auth`
- 활성화 시 라우팅:
  - `/api/v1/auth/**` -> Auth Service
  - `/api/profile`, `/api/profile/**` -> Profile Service
  - `/api/v1/users/**`, `/api/users/**` -> legacy compatibility path, internally rewritten to `/api/profile/**`
- 필수 설정:
  - `GATEWAY_OAUTH2_ISSUER_URI`
  - `GATEWAY_OAUTH2_CLIENT_ID`
  - `GATEWAY_OAUTH2_CLIENT_SECRET`
