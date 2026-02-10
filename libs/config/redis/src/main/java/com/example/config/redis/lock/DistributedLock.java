package com.example.config.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산 락 AOP 어노테이션.
 * SpEL 기반 동적 락 키를 지원한다.
 *
 * 사용 예시:
 * {@code @DistributedLock(key = "'stock:' + #stockItemId")}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** SpEL expression for lock key */
    String key();

    /** 락 획득 대기 시간 (default 3초) */
    long waitTime() default 3;

    /** 락 보유 시간 (default 5초, 비즈니스 로직 완료 후 자동 해제) */
    long leaseTime() default 5;

    /** 시간 단위 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
