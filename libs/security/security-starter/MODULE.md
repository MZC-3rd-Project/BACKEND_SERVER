# Module: `libs/security/security-starter`

## 이 모듈은 무엇인가요?
`security-starter`는 레거시 경로 호환용 wrapper입니다.
기존 서비스가 바로 깨지지 않도록 servlet/webflux starter를 묶어 제공합니다.

## 내부 위임
- `:libs:security:security-starter-servlet`
- `:libs:security:security-starter-webflux`

## 신규 코드 권장
```text
implementation(project(":libs:security:security-starter-servlet"))  // MVC 서비스
implementation(project(":libs:security:security-starter-webflux"))  // Gateway(WebFlux)
```
