# Module: `libs/security/signature`

## 한눈에 보기
- 역할: 게이트웨이 보안 헤더 서명/검증 로직을 제공합니다.
- 사용 시점: HMAC 서명 생성, 서명 기반 사용자 컨텍스트 파싱이 필요할 때 사용합니다.

## 제공 기능
- `HmacSigner`: HMAC-SHA256 서명 생성/검증
- `SignedHeaderParser`: 서명된 사용자 컨텍스트 파싱
- `SecuritySignatureAutoConfiguration`: `signing-key` 기반 Bean 자동 등록

## application.yml 설정
```yaml
app:
  security:
    context:
      signing-key: ${APP_SECURITY_CONTEXT_SIGNING_KEY:}
      max-age-millis: 300000
```

## 간단 예시
```text
implementation(project(":libs:security:signature"))
```
