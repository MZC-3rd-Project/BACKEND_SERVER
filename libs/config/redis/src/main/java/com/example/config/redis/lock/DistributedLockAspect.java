package com.example.config.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final String LOCK_PREFIX = "lock:";

    private final RedissonClient redissonClient;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = LOCK_PREFIX + parseLockKey(distributedLock.key(), joinPoint);
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!acquired) {
                throw new DistributedLockException("락 획득 실패: " + lockKey);
            }

            log.debug("락 획득 성공: {}", lockKey);
            return joinPoint.proceed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("락 획득 중 인터럽트: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("락 해제: {}", lockKey);
            }
        }
    }

    private String parseLockKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        EvaluationContext context = new StandardEvaluationContext();

        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
