# Module: `libs/api/rest-starter`

## 한눈에 보기
- 역할: REST API 기본 조합(응답 포맷 + 예외 핸들링)을 한 번에 제공합니다.
- 사용 시점: 일반적인 MVC 서비스에서 `ApiResponse`/전역 예외 처리를 함께 쓸 때 사용합니다.

## 포함 모듈
- `:libs:api:response`
- `:libs:api:exception-handler`

## 간단 예시
```text
implementation(project(":libs:api:rest-starter"))
```
