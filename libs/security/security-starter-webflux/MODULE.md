# Module: `libs/security/security-starter-webflux`

## 이 모듈이 해결하는 문제
Gateway(WebFlux)에서 사용자 컨텍스트 헤더를 일관되게 인코딩/디코딩하지 않으면 호환성이 깨집니다.
`security-starter-webflux`는 컨텍스트 헤더 codec을 제공합니다.

## 언제 사용하면 되나요?
- Gateway에서 `X-Gateway-Context`를 생성할 때
- Gateway에서 전달받은 컨텍스트를 다시 검증/해석할 때

## 핵심 제공
- `GatewayContextHeaderCodec`

## 빠른 시작
```text
implementation(project(":libs:security:security-starter-webflux"))
```
