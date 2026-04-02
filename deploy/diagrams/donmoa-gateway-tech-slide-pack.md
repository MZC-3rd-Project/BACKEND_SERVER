# Gateway 기술 선택 발표 자료

## 사용할 장표

- [donmoa-gateway-tech-slide-1-webflux.puml](./donmoa-gateway-tech-slide-1-webflux.puml)
- [donmoa-gateway-tech-slide-2-token-session.puml](./donmoa-gateway-tech-slide-2-token-session.puml)
- [donmoa-gateway-tech-slide-3-auth-relay.puml](./donmoa-gateway-tech-slide-3-auth-relay.puml)
- [donmoa-gateway-tech-slide-4-resilience.puml](./donmoa-gateway-tech-slide-4-resilience.puml)

## 장표별 핵심 메시지

- 1번 슬라이드: Gateway에서 WebFlux를 쓴 이유는 비동기 라우팅, BFF 조합, 세션 필터, WebSocket 처리까지 한 곳에서 하기 위해서다.
- 2번 슬라이드: 토큰은 브라우저에 직접 들고 있게 하지 않고, Gateway 세션으로 바꿔 관리한다.
- 3번 슬라이드: Gateway는 세션 인증 결과를 downstream 헤더로 relay하고, downstream은 공통 보안 모듈로 검증한다.
- 4번 슬라이드: 현재 Gateway의 복원력 전략은 명시적 circuit breaker보다는 timeout, fallback, degrade 패턴 중심이다.

## 슬라이드 진행 순서에 맞춘 발표 대사 상세 버전

### 1번 슬라이드 보여주고

첫 번째 슬라이드는 왜 Gateway에서 WebFlux를 썼는지를 설명하는 장표입니다.
이 Gateway는 단순히 요청 하나를 서비스 하나로 전달하는 역할만 하는 것이 아니라,
Spring Cloud Gateway 위에서 라우팅, BFF 조합, 세션 인증 필터, reactive Redis 세션 조회,
그리고 WebSocket 라우팅까지 함께 처리합니다.

즉, 요청 하나 안에서 여러 downstream을 비동기로 호출하고,
그 결과를 합치거나 보강하는 작업이 많습니다.
대표적으로 `CatalogBffService`, `CommerceReadBffService`, `BusinessProductMediaBffService` 같은 클래스들이
여러 downstream WebClient 호출을 조합해서 하나의 응답을 만듭니다.

그래서 이 장표의 핵심은
"Gateway는 fan-out / fan-in 조합이 많은 계층이기 때문에,
blocking MVC보다 WebFlux + WebClient 조합이 더 잘 맞는다"입니다.

### 2번 슬라이드 보여주고

두 번째 슬라이드는 토큰과 세션을 어떻게 관리하는지를 보여줍니다.
로그인 시작점은 `GatewayOidcController`이고,
실제 로그인 완료 흐름은 `GatewayLoginService`가 담당합니다.

이 서비스는 먼저 `GatewayAuthorizationStateService`로 OIDC state를 저장하고,
callback 시점에는 `GatewayOidcTokenClient`를 통해 authorization code를 토큰으로 교환합니다.
그 다음 받은 refresh token을 그대로 저장하지 않고,
`GatewaySessionTokenCipher`로 암호화하고
`GatewayRefreshTokenHasher`로 hash도 함께 만듭니다.

이 정보는 `GatewayServerSession` 형태로 `RedisGatewaySessionRepository`에 저장되고,
브라우저에는 `GatewaySessionCookieManager`가 `DONMOA_SESSION` 쿠키만 내려줍니다.
즉, 브라우저가 access token이나 refresh token 자체를 들고 다니는 구조가 아니라,
Gateway session id만 갖고 있고 실제 토큰 정보는 Gateway 세션 저장소가 관리하는 구조입니다.

그래서 이 장표의 핵심은
"토큰을 프론트에 직접 노출하기보다 Gateway 세션으로 감싸서 관리한다"입니다.

### 3번 슬라이드 보여주고

세 번째 슬라이드는 세션 인증 결과를 downstream으로 어떻게 전달하는지를 설명합니다.
Gateway 쪽 보안 설정은 `GatewayBffSecurityConfig`로 열려 있지만,
실제 인증 흐름은 custom filter 체인에서 처리합니다.

먼저 `GatewaySessionCookieAuthenticationWebFilter`가 세션 쿠키를 바탕으로 인증 컨텍스트를 만들고,
`GatewaySessionPrincipalResolver`가 userId와 roles를 해석합니다.
그 다음 `SessionHeaderRelayGlobalFilter`가 이 값을
`X-Gateway-Context`, `X-User-Id`, `X-User-Roles`, `X-Session-Id` 같은 내부 헤더로 바꿔 downstream에 전달합니다.

그리고 downstream 서비스는 `GatewaySecurityValidationFilter`, `GatewayRequestVerifier` 같은 공통 보안 모듈로
이 헤더를 검증합니다.
즉, 각 서비스가 인증 로직을 다시 구현하지 않고,
Gateway는 relay를 하고 downstream은 공통 모듈로 검증하는 구조입니다.

이 장표의 핵심은
"Gateway가 인증 결과를 전달하고, downstream은 공통 보안 모듈로 검증한다"입니다.

### 4번 슬라이드 보여주고

네 번째 슬라이드는 게이트웨이의 복원력 전략을 설명합니다.
여기서 먼저 말씀드릴 점은,
이 리포에는 `libs/config/resilience`라는 공통 CircuitBreaker 모듈이 존재하지만,
현재 게이트웨이 코드에서 눈에 띄는 주된 패턴은 명시적 `CircuitBreakerHelper` 호출보다는
`timeout + onErrorResume + fallback + degrade` 조합입니다.

예를 들어 `SearchClickRelayService`는 500밀리초 timeout을 두고,
실패하면 사용자 흐름을 막지 않도록 그냥 `Mono.empty()`로 처리합니다.
`CatalogBffService`는 오류가 발생했을 때 degrade query로 fallback 하는 패턴을 가지고 있고,
`CatalogDetailBffService`나 `BusinessProductMediaBffService`도 `onErrorResume`, `onErrorReturn` 기반 fallback을 사용합니다.

즉, 현재 Gateway의 복원력 전략은
"강한 서킷브레이커 선언형 제어"보다는
"짧은 timeout과 명시적 fallback, degrade 경로"로 사용자 경험을 유지하는 방향에 가깝습니다.

그래서 이 장표의 핵심은
"게이트웨이는 현재 timeout + fallback 중심으로 복원력을 만들고 있고,
필요하면 공통 resilience 모듈로 더 강화할 여지는 있다"입니다.

### 마무리 멘트

정리하면,
Gateway에서 WebFlux를 쓴 이유는 비동기 fan-out/fan-in, 세션 필터, WebSocket 라우팅까지 한 계층에서 처리해야 하기 때문입니다.
토큰은 브라우저에 직접 남기지 않고 Gateway 세션으로 관리하고,
세션 인증 결과는 내부 헤더로 downstream에 relay합니다.
그리고 복원력은 현재 코드 기준으로 explicit circuit breaker보다 timeout, fallback, degrade 패턴 중심으로 구현돼 있습니다.

즉, 이 Gateway는 단순 라우터가 아니라
비동기 BFF, 세션 보안 경계, 그리고 사용자 경험 보호를 동시에 맡는 기술적 관문 계층이라고 보시면 됩니다.

## 짧은 발표 버전

Gateway에서 WebFlux를 쓴 이유는 여러 downstream 호출 조합, 세션 필터, WebSocket 라우팅을 비동기로 처리하기 위해서입니다.
토큰은 브라우저에 직접 두지 않고 Gateway 세션으로 저장하며, refresh token은 암호화와 hash를 함께 사용합니다.
세션 인증 결과는 내부 헤더로 downstream에 relay하고, downstream은 공통 보안 모듈로 검증합니다.
현재 복원력 전략은 explicit circuit breaker보다는 timeout, fallback, degrade 패턴 중심입니다.

## 발표자 메모

- 1번 슬라이드는 "왜 WebFlux인지"
- 2번 슬라이드는 "토큰/세션 관리"
- 3번 슬라이드는 "downstream 보안 relay"
- 4번 슬라이드는 "복원력 전략"
- 서킷브레이커 질문이 나오면 "공통 모듈은 있지만, gateway 현재 구현은 fallback/degrade 중심"이라고 답하면 정확하다
