# Module: `libs/contracts/http`

## 이 모듈이 해결하는 문제
헤더 이름을 문자열로 하드코딩하면 오타/불일치가 쉽게 발생합니다.
`contracts/http`는 서비스 간 공유하는 HTTP 헤더 상수를 한 곳에 모아 계약을 고정합니다.

## 언제 사용하면 되나요?
- Gateway와 서비스가 같은 헤더 키를 써야 할 때
- 인증/컨텍스트 헤더명을 코드 전역에서 일관되게 유지하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:contracts:http"))
```

```java
request.getHeader(HttpHeaderNames.USER_ID);
```
