# Module: `libs/config/tracing`

## 이 모듈이 해결하는 문제
요청 단위 추적 ID가 없으면 로그를 따라가며 장애를 분석하기 어렵습니다.
`config/tracing`은 MDC 기반 추적 필터를 공통으로 제공합니다.

## 언제 사용하면 되나요?
- 분산 서비스에서 요청 흐름을 로그로 추적해야 할 때
- traceId를 서비스 전체에 일관되게 넣고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:config:tracing"))
```

```yaml
app:
  tracing:
    enabled: true
```
