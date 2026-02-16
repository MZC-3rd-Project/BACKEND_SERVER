package com.example.config.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final String LOCK_PREFIX = "lock:";

    private final ObjectProvider<DistributedLockExecutor> lockExecutorProvider;
    private final ExpressionParser parser;

    @Around("@annotation(com.example.config.lock.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DistributedLockExecutor lockExecutor = lockExecutorProvider.getIfAvailable();
        if (lockExecutor == null) {
            throw new DistributedLockException(
                    "DistributedLockExecutor bean not found. " +
                            "Add :libs:config:lock-redisson or provide custom executor implementation."
            );
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String lockKey = LOCK_PREFIX + parseLockKey(distributedLock.key(), joinPoint);
        boolean acquired = false;
        try {
            acquired = lockExecutor.tryLock(
                    lockKey,
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
            if (acquired && lockExecutor.isHeldByCurrentThread(lockKey)) {
                lockExecutor.unlock(lockKey);
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
