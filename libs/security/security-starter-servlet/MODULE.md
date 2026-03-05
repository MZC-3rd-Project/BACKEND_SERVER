# Module: `libs/security/security-starter-servlet`

## 한눈에 보기
- 역할: Servlet 기반 서비스의 Gateway 헤더 검증/사용자 컨텍스트 주입을 제공합니다.
- 사용 시점: MVC 서비스에서 `@CurrentUserId`와 `X-Gateway-*` 검증이 필요할 때 사용합니다.

## 핵심 제공
- `GatewaySecurityAutoConfiguration`
- `GatewaySecurityValidationFilter`
- `@CurrentUserId`, `CurrentUserIdArgumentResolver`

## 패키지 맵
- `com.example.security.starter.servlet.annotation`
  - `CurrentUserId`: 컨트롤러 파라미터 어노테이션
- `com.example.security.starter.servlet.resolver`
  - `CurrentUserIdArgumentResolver`: `@CurrentUserId` 해석기
- `com.example.security.starter.servlet.properties`
  - `GatewaySecurityModuleProperties`: `app.gateway-security.*` 설정 모델
  - `GatewayHeaderValidation*`: 헤더 검증 설정 계약
- `com.example.security.starter.servlet.client`
  - `GatewaySecurityClient`: 검증 인터페이스
- `com.example.security.starter.servlet.verifier`
  - `GatewayRequestVerifier`: 실제 헤더 검증 구현
- `com.example.security.starter.servlet.filter`
  - `GatewaySecurityValidationFilter`: 요청 필터 진입점
- `com.example.security.starter.servlet.config`
  - `GatewaySecurityWebMvcConfigurer`: MVC argument resolver 등록
- `com.example.security.starter.servlet.autoconfigure`
  - `GatewaySecurityAutoConfiguration`: 자동 설정 조립

## 설정 키
```yaml
app:
  gateway-security:
    enabled: true
    required-paths:
      - /api/v1/**
```

