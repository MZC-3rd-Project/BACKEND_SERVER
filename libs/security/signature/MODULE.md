# Module: `libs/security/signature`

## 이 모듈이 해결하는 문제
Gateway가 내려준 사용자 컨텍스트를 위변조 없이 신뢰하려면 서명 검증이 필요합니다.
`security/signature`는 HMAC 서명 생성/검증과 파서를 제공합니다.

## 언제 사용하면 되나요?
- Gateway 헤더 서명을 생성하거나 검증할 때
- `X-Gateway-Context` 기반 사용자 컨텍스트를 안전하게 파싱할 때

## 핵심 제공
- `HmacSigner`
- `SignedHeaderParser`
- `SecuritySignatureAutoConfiguration`

## 빠른 시작
```text
implementation(project(":libs:security:signature"))
```

```yaml
app:
  security:
    context:
      signing-key: ${APP_SECURITY_CONTEXT_SIGNING_KEY:}
      max-age-millis: 300000
```
