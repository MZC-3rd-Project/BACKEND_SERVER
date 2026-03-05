# Module: `libs/api/exception-handler`

## 이 모듈이 해결하는 문제
서비스마다 `@RestControllerAdvice`를 따로 구현하면 예외 코드/메시지 포맷이 자주 달라집니다.
`exception-handler`는 공통 예외를 표준 응답으로 변환해 API 일관성을 유지합니다.

## 언제 사용하면 되나요?
- `BusinessException`, `TechnicalException`을 공통 규칙으로 응답하고 싶을 때
- 서비스마다 중복된 예외 처리 코드를 줄이고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:api:exception-handler"))
```

## 알아두면 좋은 점
- 이 모듈은 `:libs:api:response`에 의존합니다.
- 신규 서비스는 보통 `:libs:api:rest-starter` 하나만 추가하면 충분합니다.
