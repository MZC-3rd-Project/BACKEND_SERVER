# Module: `libs/data/entity`

## 이 모듈은 무엇인가요?
`entity`는 레거시 경로 호환용 wrapper입니다.
기존 서비스가 바로 깨지지 않도록 `jpa-base`와 `rw-routing`을 묶어 제공합니다.

## 내부 위임
- `:libs:data:jpa-base`
- `:libs:data:rw-routing`

## 신규 코드 권장
```text
implementation(project(":libs:data:jpa-base"))
implementation(project(":libs:data:rw-routing"))
```
