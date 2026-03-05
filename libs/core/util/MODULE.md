# Module: `libs/core/util`

## 이 모듈이 해결하는 문제
서비스마다 JSON 유틸/문자열 유틸을 따로 만들면 중복 코드가 빠르게 늘어납니다.
`core/util`은 공통 유틸을 제공해 반복 구현을 줄입니다.

## 언제 사용하면 되나요?
- 이벤트 payload 직렬화/역직렬화가 자주 필요할 때
- 여러 서비스에서 같은 유틸 로직을 재사용하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:core:util"))
```

```java
String json = JsonUtils.toJson(payload);
```
