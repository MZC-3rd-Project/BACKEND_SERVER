# Module: `libs/config/lock`

## 이 모듈이 해결하는 문제
동시에 같은 자원을 갱신할 때 데이터 불일치가 발생할 수 있습니다.
`config/lock`은 `@DistributedLock` AOP와 락 인터페이스를 제공합니다.

## 언제 사용하면 되나요?
- 재고 차감, 중복 참여 방지 같은 동시성 제어가 필요할 때
- 비즈니스 코드에서 락 구현체(Redis 등)를 분리하고 싶을 때

## 빠른 시작
```text
implementation(project(":libs:config:lock"))
```

```java
@DistributedLock(key = "'stock:' + #request.stockItemId")
public void decreaseStock(...) { ... }
```

## 주의
이 모듈은 락 "인터페이스/어노테이션"만 제공합니다.
실제 실행을 위해 `:libs:config:lock-redisson` 또는 사용자 구현체가 필요합니다.
