# Module: `libs/core/exception`

## 이 모듈이 해결하는 문제
예외 타입과 에러코드 체계가 팀마다 달라지면 장애 대응이 어렵습니다.
`core/exception`은 공통 예외 계층과 에러코드 계약을 제공해 예외 표현을 표준화합니다.

## 언제 사용하면 되나요?
- 도메인 오류/인프라 오류를 명확히 구분하고 싶을 때
- API 에러 응답과 내부 예외 체계를 연결하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:core:exception"))
```

```java
throw new BusinessException(DomainErrorCode.NOT_FOUND);
```
