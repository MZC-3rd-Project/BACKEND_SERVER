# Module: `libs/core/pagination`

## 이 모듈이 해결하는 문제
목록 API마다 페이지 응답 구조가 다르면 클라이언트가 매번 별도 처리해야 합니다.
`core/pagination`은 커서/오프셋 기반 응답 모델을 표준화합니다.

## 언제 사용하면 되나요?
- 목록 API 응답 구조를 통일하고 싶을 때
- 커서 페이징을 서비스 간 같은 방식으로 운영하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:core:pagination"))
```

```java
return CursorResponse.of(items, nextCursor);
```
