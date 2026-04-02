# Gateway 한 장 소개 슬라이드

장표 파일: [donmoa-gateway-one-slide-overview.drawio](./donmoa-gateway-one-slide-overview.drawio)

## 장표 목적

이 장표는 Gateway를 세부 흐름으로 설명하기 전에,
이 계층이 전체 아키텍처에서 어떤 역할을 맡는지를 한 장에서 설명하기 위한 소개 슬라이드다.

## 발표 대사 1분 버전

이 장표는 우리 Gateway를 한 문장으로 설명하기 위한 소개 슬라이드입니다.
Gateway는 단순히 요청을 다른 서비스로 넘기는 프록시가 아니라,
첫째로 라우팅과 엔트리 포인트 역할을 하고,
둘째로 세션과 토큰을 관리하는 보안 경계 역할을 하고,
셋째로 여러 downstream 응답을 조합하는 BFF 역할을 하고,
넷째로 timeout, fallback, degrade 같은 방식으로 사용자 경험을 보호하는 역할도 합니다.

즉, Gateway는 경로 분기만 하는 얇은 레이어가 아니라,
클라이언트 복잡도를 줄이고, 보안 정책을 중앙화하고,
응답 조합과 장애 대응까지 맡는 Edge 계층입니다.
그리고 이런 역할이 한 곳에 모여 있기 때문에 WebFlux와 Spring Cloud Gateway 조합을 선택했다고 설명하시면 됩니다.

## 발표 대사 상세 버전

이 장표는 Gateway를 세부 흐름 하나하나로 설명하기 전에,
이 계층이 어떤 기능을 맡고 있는지를 한 장에서 설명하기 위한 슬라이드입니다.

가장 위의 `Gateway` 박스를 보시면,
이 프로젝트에는 `client-gateway`와 `business-gateway` 두 종류의 gateway가 있습니다.
즉, 하나의 gateway가 모든 역할을 다 하는 것이 아니라,
읽기 중심 사용자 경험과 비즈니스 업무 흐름을 나눠서 담당하고 있습니다.

왼쪽 위 `Routing & Entry`를 보시면,
Gateway는 외부 요청의 진입점이자 라우팅 계층입니다.
`spring.cloud.gateway.routes`, `GatewayAuthRouteConfig`, WebSocket route 같은 설정과 클래스를 보면,
클라이언트는 내부 서비스 위치를 몰라도 되고,
Gateway가 도메인별 진입과 경로 분기를 맡고 있다는 걸 알 수 있습니다.

오른쪽 위 `Session & Token Boundary`는
Gateway가 보안 경계 역할을 한다는 뜻입니다.
OIDC 로그인 이후 Gateway가 Redis session을 만들고,
브라우저에는 세션 쿠키만 주며,
세션 인증 결과는 signed context header 형태로 downstream에 relay합니다.
즉, 토큰을 프론트와 여러 서비스가 직접 주고받는 대신,
Gateway가 인증 결과를 내부 서비스가 쓸 수 있는 형태로 바꿔 주는 구조입니다.

왼쪽 아래 `BFF Orchestration`은
Gateway가 단순 전달 계층이 아니라 BFF 계층이라는 뜻입니다.
예를 들어 `CatalogBffService`는 검색 응답을 media URL로 보강하고,
`BusinessProductMediaBffService`는 상품 생성/수정 뒤에 이미지 작업과 상세 재조회를 하나의 흐름으로 묶습니다.
즉, 클라이언트는 여러 downstream을 직접 조합하지 않고,
Gateway가 정리한 결과만 받게 됩니다.

오른쪽 아래 `User Experience Protection`은
Gateway가 장애 대응도 일부 맡는다는 의미입니다.
현재 코드 기준으로는 explicit circuit breaker를 강하게 쓰기보다,
WebClient timeout, onErrorResume, degrade fallback, best effort relay 패턴을 많이 사용합니다.
즉, 사용자 요청이 실패했을 때 가능한 한 전체 경험이 무너지지 않도록 완충 계층 역할도 하고 있습니다.

맨 아래 `왜 WebFlux?`를 보시면,
이 모든 역할이 비동기 fan-out/fan-in, custom filter chain, reactive Redis session, WebSocket 처리와 연결됩니다.
그래서 Gateway는 MVC보다 WebFlux와 Spring Cloud Gateway 조합이 더 적합하다고 설명하시면 됩니다.

마지막으로 한 문장 정리를 보면,
Gateway는 단순 경로 분기 계층이 아니라
라우팅, 세션/보안 경계, BFF 조합, 사용자 경험 보호를 맡는 Edge 계층이라고 보시면 됩니다.

## 발표자 메모

- 이 장표는 세부 흐름 설명 전에 "Gateway를 어떻게 정의할 것인가"를 먼저 잡는 용도로 쓰면 좋다.
- 핵심 반복 문장은 `Gateway는 단순 프록시가 아니라 Edge + BFF + Security Boundary`로 잡으면 된다.
- 이후에 세션 관리나 BFF 흐름 장표로 넘어가면 연결이 자연스럽다.
