# Module: `libs/security/security-starter-servlet`

## 이 모듈이 해결하는 문제
MVC 서비스에서 Gateway 헤더 검증, 사용자 ID 주입, 필터 등록을 매번 직접 구현하면 누락이 자주 생깁니다.
`security-starter-servlet`은 이를 자동설정으로 제공합니다.

## 언제 사용하면 되나요?
- Controller 파라미터에서 `@CurrentUserId`를 쓰고 싶을 때
- `X-Gateway-*` 헤더 검증을 서비스 공통으로 적용하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:security:security-starter-servlet"))
```

```yaml
app:
  gateway-security:
    enabled: true
    required-paths:
      - /api/v1/**
```

## 패키지 맵 (처음 보는 사람용)
- `annotation`: 컨트롤러 파라미터 어노테이션 (`@CurrentUserId`)
- `resolver`: 어노테이션을 실제 userId로 변환
- `properties`: `app.gateway-security.*` 설정 모델
- `client`: 검증 인터페이스
- `verifier`: 헤더 검증 구현체
- `filter`: HTTP 요청 필터
- `config`: MVC resolver 등록
- `autoconfigure`: Bean 조립 진입점
