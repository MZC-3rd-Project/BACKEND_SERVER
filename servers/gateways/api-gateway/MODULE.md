# Module: `servers/gateways/api-gateway`

## 한눈에 보기
- 역할: 외부 요청 진입점 게이트웨이 모듈입니다.
- 사용 시점: 인증/라우팅/헤더 주입 정책을 적용할 때 사용합니다.

## 이 서비스가 맡는 책임
이 모듈은 특정 도메인의 API와 비즈니스 규칙을 처리하는 독립 서비스입니다.
컨트롤러는 요청/응답 변환에 집중하고, 핵심 규칙은 서비스 계층에서 처리하며,
데이터 저장/조회는 리포지토리로 분리해 변경 영향을 최소화합니다.

## 간단 예시
```text
./gradlew :servers:gateways:api-gateway:bootRun
```

## 보안 헤더 서명
- `APP_SECURITY_CONTEXT_SIGNING_KEY`를 설정하면 Gateway가 사용자 컨텍스트 헤더를 HMAC으로 서명해 전달합니다.
- Chat 같은 소비 서비스도 같은 키를 사용해야 검증이 통과합니다.
