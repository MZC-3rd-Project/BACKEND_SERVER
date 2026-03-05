# Module: `libs/config/locking`

## 한눈에 보기
- 역할: 분산락 공통 조합(`lock` + `lock-redisson`)을 제공합니다.
- 사용 시점: 서비스에서 동시성 제어를 해야 하지만 의존성을 매번 따로 적기 싫을 때 사용합니다.

## 포함 모듈
- `:libs:config:lock`
- `:libs:config:lock-redisson`

## 간단 예시
```text
implementation(project(":libs:config:locking"))
@DistributedLock(key = "'stock:' + #request.stockItemId")
```
