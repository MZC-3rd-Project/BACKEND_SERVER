# Module: `libs/security/context`

## 이 모듈이 해결하는 문제
요청 단위 인증 정보(`userId`, `roles`)를 서비스 전역에서 일관되게 접근하기 어렵습니다.
`security/context`는 AuthContext 저장/조회와 요청 종료 시 정리를 담당합니다.

## 언제 사용하면 되나요?
- 서비스 로직에서 현재 사용자 정보를 읽어야 할 때
- ThreadLocal 컨텍스트 누수 방지(요청 종료 정리)가 필요할 때

## 빠른 시작
```text
implementation(project(":libs:security:context"))
```

```yaml
app:
  security:
    context:
      cleanup-filter-enabled: true
```
