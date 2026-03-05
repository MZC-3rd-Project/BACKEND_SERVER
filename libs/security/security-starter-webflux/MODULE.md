# Module: `libs/security/security-starter-webflux`

## 한눈에 보기
- 역할: Gateway 사용자 컨텍스트 헤더 인코딩/디코딩 유틸을 제공합니다.
- 사용 시점: Gateway(WebFlux)에서 `X-Gateway-Context`를 생성/해석할 때 사용합니다.

## 핵심 제공
- `GatewayContextHeaderCodec`

## 간단 예시
```text
implementation(project(":libs:security:security-starter-webflux"))
GatewayContextHeaderCodec.encodeSigned(userId, roles, nonce, timestamp, signer)
```
