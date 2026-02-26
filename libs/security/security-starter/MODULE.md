# Module: `libs/security/security-starter`

## 한눈에 보기
- 역할: Gateway가 주입한 인증/컨텍스트 헤더를 검증합니다.
- 사용 시점: 다운스트림 서비스에서 `X-Gateway-Auth` 및 사용자 컨텍스트 헤더 검증이 필요할 때 사용합니다.

## 제공 기능
- `GatewaySecurityClient` 인터페이스 기반 검증 진입점
- 내부 인증 헤더(`X-Gateway-Auth`) 검증
- 사용자 컨텍스트 헤더(`X-Gateway-Context`) 필수/선택 검증
- HMAC 서명 검증(`SignedHeaderParser`) 연동
- `app.gateway-security.enabled=true` 시 필터/리졸버 자동 등록
- `@CurrentUserId` 인자 주입 지원

## 간단 예시
```text
implementation(project(":libs:security:security-starter"))
```

```yaml
app:
  gateway-security:
    enabled: true
    required-paths:
      - /api/v1/chat/**
    optional-user-context-paths:
      - /api/v1/search/**
```
