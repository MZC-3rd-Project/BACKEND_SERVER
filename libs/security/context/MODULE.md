# Module: `libs/security/context`

## 한눈에 보기
- 역할: 인증 컨텍스트(AuthContext), 서명 파서, 요청 컨텍스트 정리를 담당합니다.
- 사용 시점: 게이트웨이 주입 헤더 검증/컨텍스트 접근이 필요할 때 사용합니다.

## 모듈이 필요한 이유
공통 기능을 서비스마다 다시 만들면 구현이 조금씩 달라지고 유지보수 포인트가 급격히 늘어납니다.
이 모듈은 팀 공통 정책을 한 곳으로 모아 "중복 제거 + 일관성 유지 + 변경 비용 절감"을 만드는 목적입니다.

## 적용 순서
1. `build.gradle.kts`에 모듈 의존성을 추가합니다.
2. 필요한 설정 키를 `application.yml`에 채웁니다.
3. 기존 중복 코드를 모듈 API로 대체합니다.

## application.yml 설정
```yaml
app:
  security:
    context:
      cleanup-filter-enabled: true
      signing-key: ${GATEWAY_SIGNING_KEY:}
      max-age-millis: 300000
```

- `cleanup-filter-enabled` 기본값은 `true`입니다.
- `signing-key`는 `SignedHeaderParser`를 사용할 때 필수이며, 빈 문자열이면 애플리케이션이 fail-fast로 시작 실패합니다.
- `signing-key`를 아예 설정하지 않으면 `HmacSigner`/`SignedHeaderParser`는 등록되지 않습니다.

## 간단 예시
```text
implementation(project(":libs:security:context"))

AuthContext context = signedHeaderParser.parse(request::getHeader);
```
