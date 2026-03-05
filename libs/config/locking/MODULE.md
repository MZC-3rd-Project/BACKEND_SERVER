# Module: `libs/config/locking`

## 이 모듈이 해결하는 문제
`lock` + `lock-redisson`을 항상 함께 추가해야 해서 의존성 누락이 자주 발생합니다.
`locking`은 분산락 기본 조합을 starter처럼 제공합니다.

## 포함된 모듈
- `:libs:config:lock`
- `:libs:config:lock-redisson`

## 언제 사용하면 되나요?
- 신규 서비스에서 분산락을 빠르게 적용할 때
- 의존성 누락 실수를 줄이고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:config:locking"))
```
