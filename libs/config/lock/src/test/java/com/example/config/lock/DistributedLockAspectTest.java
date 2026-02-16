package com.example.config.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DistributedLockAspectTest {

    @Test
    void shouldThrowWhenExecutorIsMissing() throws Throwable {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        DistributedLockAspect aspect = new DistributedLockAspect(
                beanFactory.getBeanProvider(DistributedLockExecutor.class),
                new SpelExpressionParser()
        );

        ProceedingJoinPoint joinPoint = mockJoinPoint(7L);

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(DistributedLockException.class)
                .hasMessageContaining("DistributedLockExecutor bean not found");
    }

    @Test
    void shouldExecuteAndUnlockWhenExecutorExists() throws Throwable {
        InMemoryLockExecutor executor = Mockito.spy(new InMemoryLockExecutor());
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("lockExecutor", executor);

        DistributedLockAspect aspect = new DistributedLockAspect(
                beanFactory.getBeanProvider(DistributedLockExecutor.class),
                new SpelExpressionParser()
        );

        ProceedingJoinPoint joinPoint = mockJoinPoint(9L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("ok");
        Mockito.verify(executor).unlock("lock:item:9");
    }

    private ProceedingJoinPoint mockJoinPoint(Long id) throws NoSuchMethodException {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = SampleService.class.getDeclaredMethod("reserve", Long.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"itemId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{id});
        return joinPoint;
    }

    static class SampleService {
        @DistributedLock(key = "'item:' + #itemId")
        void reserve(Long itemId) {
        }
    }

    static class InMemoryLockExecutor implements DistributedLockExecutor {
        private final Set<String> lockedKeys = ConcurrentHashMap.newKeySet();

        @Override
        public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
            return lockedKeys.add(lockKey);
        }

        @Override
        public boolean isHeldByCurrentThread(String lockKey) {
            return lockedKeys.contains(lockKey);
        }

        @Override
        public void unlock(String lockKey) {
            lockedKeys.remove(lockKey);
        }
    }
}
