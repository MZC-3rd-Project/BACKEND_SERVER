# Module: `libs/config/webclient`

## 이 모듈이 해결하는 문제
서비스 간 HTTP 호출을 각 서비스가 제각각 설정하면 타임아웃/에러처리 정책이 달라집니다.
`config/webclient`는 WebClient 공통 설정을 한 곳에서 관리합니다.

## 언제 사용하면 되나요?
- 외부 API/내부 서비스 호출을 WebClient로 할 때
- 연결/읽기 타임아웃 정책을 표준화하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:config:webclient"))
```

```yaml
app:
  webclient:
    connect-timeout: 5000
    read-timeout: 10000
```
