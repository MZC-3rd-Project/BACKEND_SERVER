# Module: `libs/api/rest-starter`

## 이 모듈이 해결하는 문제
처음 서비스를 만들 때 `response`와 `exception-handler`를 매번 같이 추가하는 실수를 줄이기 위해,
REST API 기본 조합을 starter 하나로 제공합니다.

## 포함된 모듈
- `:libs:api:response`
- `:libs:api:exception-handler`

## 언제 사용하면 되나요?
- 신규 MVC 서비스를 만들 때
- 공통 응답 + 공통 예외 처리를 한 번에 적용하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:api:rest-starter"))
```
