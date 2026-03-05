# Module: `libs/openapi/config`

## 이 모듈이 해결하는 문제
서비스마다 OpenAPI 설정이 다르면 문서 품질과 사용자 경험이 크게 흔들립니다.
`openapi/config`는 공통 문서화 설정을 제공합니다.

## 언제 사용하면 되나요?
- Swagger/OpenAPI 문서를 서비스 간 동일한 정책으로 유지할 때
- 공통 헤더/공통 응답 규칙을 문서에 반영하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:openapi:config"))
```

```yaml
app:
  openapi:
    enabled: true
    title: Project03 API
```
