# Gateway 한 장 소개 슬라이드

장표 파일: [donmoa-gateway-one-slide-overview.puml](./donmoa-gateway-one-slide-overview.puml)

## 이 장표의 목적

이 장표는 Gateway를 흐름도로 설명하는 대신,
"이 계층이 도대체 무슨 역할을 하는가"를 한 장에서 설명하기 위한 소개 슬라이드다.

## 장표 핵심 메시지

- Gateway는 단순 프록시가 아니다.
- 라우팅, 세션/보안 경계, BFF 조합, 복원력 관리가 한 계층에 모여 있다.
- WebFlux를 쓴 이유도 바로 이 역할 때문이다.

## 발표 대사 1분 버전

이 장표는 우리 Gateway를 한 문장으로 설명하기 위한 소개 슬라이드입니다.
Gateway는 단순히 요청을 다른 서비스로 넘기는 프록시가 아니라,
첫째로 라우팅과 엔트리 포인트 역할을 하고,
둘째로 세션과 토큰을 관리하는 보안 경계 역할을 하고,
셋째로 여러 downstream 응답을 조합하는 BFF 계층 역할을 하고,
넷째로 timeout, fallback, degrade 같은 복원력 전략으로 사용자 경험을 보호하는 역할도 합니다.

즉, Gateway는 경로 분기만 하는 얇은 레이어가 아니라,
클라이언트 복잡도를 줄이고 보안 정책을 중앙화하고 응답 조합과 장애 대응까지 맡는 Edge 계층입니다.
그리고 이런 역할을 한 곳에서 처리해야 했기 때문에 Spring Cloud Gateway와 WebFlux 조합을 사용했다고 설명하시면 됩니다.

## 발표 대사 상세 버전

이 장표는 Gateway를 세부 흐름 하나하나로 설명하기 전에,
이 계층이 전체 아키텍처에서 어떤 역할을 맡는지를 먼저 보여주기 위한 장표입니다.

가장 위의 `Gateway` 박스를 보시면,
이 프로젝트에는 `client-gateway`와 `business-gateway` 두 종류의 gateway가 있습니다.
여기서 중요한 점은 이 둘이 단순히 URL만 다른 것이 아니라,
같은 gateway 패턴 위에서 서로 다른 사용자 맥락을 담당한다는 점입니다.

왼쪽 위 `Routing & Entry` 영역은
Gateway가 외부 요청의 진입점이자 라우팅 계층이라는 의미입니다.
`GatewayAuthRouteConfig`나 각 gateway의 `application.yml` route 설정을 보면
도메인별 API 경로, rewrite 경로, WebSocket 경로까지 gateway가 관리하고 있습니다.
즉, 외부 클라이언트는 내부 서비스 위치를 몰라도 되고,
Gateway가 요청 진입과 경로 분기를 맡습니다.

오른쪽 위 `Session & Token Boundary` 영역은
Gateway가 보안 경계라는 의미입니다.
이 프로젝트는 브라우저가 access token과 refresh token을 직접 들고 다니게 하기보다,
OIDC 로그인 이후 Gateway session으로 바꿔서 Redis에 저장하고,
세션 쿠키를 기준으로 사용자를 식별합니다.
그리고 그 결과를 `SessionHeaderRelayGlobalFilter`가 signed context header 같은 내부 헤더로 relay해서
downstream 서비스가 공통 보안 모듈로 검증할 수 있게 합니다.
즉, 인증 정보를 가장 먼저 받아서 내부 서비스가 쓸 수 있는 형태로 바꿔 주는 경계가 Gateway입니다.

왼쪽 아래 `BFF Orchestration` 영역은
Gateway가 단순 전달 계층이 아니라 BFF 계층이라는 의미입니다.
예를 들어 `CatalogBffService`는 검색 결과를 받아서 media URL을 보강하고,
`BusinessProductMediaBffService`는 상품 생성/수정 뒤에 이미지 작업과 상세 재조회를 하나의 흐름으로 묶습니다.
즉, 클라이언트가 여러 downstream 호출을 직접 조합하지 않게 만들고,
클라이언트 친화적인 응답이나 업무 흐름으로 재구성하는 역할을 Gateway가 맡습니다.

오른쪽 아래 `User Experience Protection` 영역은
Gateway가 장애 대응도 어느 정도 책임진다는 의미입니다.
현재 코드 기준으로는 explicit circuit breaker를 곳곳에 박아 넣었다기보다,
WebClient timeout, onErrorResume, fallback, degrade 패턴을 많이 사용하고 있습니다.
예를 들어 일부 호출은 실패해도 사용자 흐름을 막지 않는 best effort로 보내고,
검색 응답은 degrade fallback 경로를 두는 식입니다.
즉, Gateway는 단순 연결점이 아니라 사용자 경험을 지키는 완충 계층이기도 합니다.

아래 `왜 WebFlux?` 박스를 보시면,
이 모든 역할이 비동기 fan-out/fan-in, custom filter chain, reactive Redis session, WebSocket route 같은 특성과 맞물려 있습니다.
그래서 이 Gateway는 MVC보다 WebFlux와 Spring Cloud Gateway 조합이 더 잘 맞습니다.

마지막으로 맨 아래 한 문장 정리를 보시면,
이 Gateway는 단순 경로 분기 계층이 아니라
라우팅, 세션/보안 경계, BFF 조합, 복원력 관리가 한 곳에 모인 Edge 계층이라고 설명하시면 됩니다.

## 발표자 메모

- 이 장표는 세부 흐름 설명 전에 "Gateway를 한 문장으로 규정"하는 용도로 쓰면 좋다.
- 발표 시작 1~2분 안에 이 장표를 먼저 보여주고, 이후 세션 관리나 BFF 흐름 장표로 들어가면 연결이 자연스럽다.
- 핵심 반복 문장은 "Gateway는 단순 프록시가 아니라 Edge + BFF + Security Boundary"로 잡으면 좋다.
