# Module: `libs/config/shedlock`

## 이 모듈이 해결하는 문제
멀티 인스턴스에서 스케줄러가 동시에 실행되면 중복 작업이 발생합니다.
`shedlock`은 스케줄 작업을 단일 실행으로 제한하는 설정을 제공합니다.

## 언제 사용하면 되나요?
- 배치/정리 스케줄을 인스턴스 1개만 실행하고 싶을 때
- Redis 기반 분산 스케줄 락이 필요할 때

## 빠른 시작
```text
implementation(project(":libs:config:shedlock"))
```

```java
@SchedulerLock(name = "cleanupJob")
@Scheduled(cron = "0 0/5 * * * *")
public void cleanup() { ... }
```
