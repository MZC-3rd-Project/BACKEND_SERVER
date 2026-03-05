# Module: `libs/api/response`

## 이 모듈이 해결하는 문제
서비스마다 응답 JSON 형식이 달라지면, 프론트/클라이언트가 API마다 다른 파싱 로직을 가져야 합니다.
`response` 모듈은 `ApiResponse`/`ErrorResponse`를 공통으로 제공해 응답 형식을 통일합니다.

## 언제 사용하면 되나요?
- Controller 응답을 공통 포맷으로 감싸고 싶을 때
- 성공/실패 응답 스키마를 서비스 전체에서 일관되게 유지하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:api:response"))
```

```java
return ApiResponse.success(data);
```

## 보통 같이 쓰는 모듈
- `:libs:api:exception-handler` (예외도 같은 포맷으로 맞춤)
- `:libs:api:rest-starter` (두 모듈을 한 번에 사용)
