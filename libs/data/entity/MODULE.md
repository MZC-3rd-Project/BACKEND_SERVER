# Module: `libs/data/entity`

## 한눈에 보기
- 역할: 데이터 레이어 레거시 경로 호환용 wrapper 모듈입니다.
- 사용 시점: 기존 `:libs:data:entity` 의존성을 즉시 변경하기 어려운 경우 사용합니다.

## 내부 위임
- `:libs:data:jpa-base`
- `:libs:data:rw-routing`

## 권장
신규 코드에서는 역할별 모듈을 직접 사용하세요.
```text
implementation(project(":libs:data:jpa-base"))
implementation(project(":libs:data:rw-routing"))
```
