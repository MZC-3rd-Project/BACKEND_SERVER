package com.example.config.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerHelper {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final FallbackRegistry fallbackRegistry;

    public <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        return CircuitBreaker.decorateSupplier(cb, supplier).get();
    }

    public <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier, Supplier<T> fallback) {
        try {
            T result = executeWithCircuitBreaker(name, supplier);
            fallbackRegistry.cacheResponse(name, result);
            return result;
        } catch (Exception e) {
            log.warn("[CircuitBreaker] {} fallback triggered: {}", name, e.getMessage());
            return fallback.get();
        }
    }

    public <T> T executeWithRetry(String name, Supplier<T> supplier) {
        Retry retry = retryRegistry.retry(name);
        return Retry.decorateSupplier(retry, supplier).get();
    }

    public <T> T executeWithCircuitBreakerAndRetry(String name, Supplier<T> supplier) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        Retry retry = retryRegistry.retry(name);
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(cb,
                Retry.decorateSupplier(retry, supplier));
        return decorated.get();
    }

    public <T> T executeWithCircuitBreakerAndRetry(String name, Supplier<T> supplier, Supplier<T> fallback) {
        try {
            T result = executeWithCircuitBreakerAndRetry(name, supplier);
            fallbackRegistry.cacheResponse(name, result);
            return result;
        } catch (Exception e) {
            log.warn("[CircuitBreaker+Retry] {} fallback triggered: {}", name, e.getMessage());
            return fallback.get();
        }
    }

    public void runWithCircuitBreaker(String name, Runnable runnable) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        CircuitBreaker.decorateRunnable(cb, runnable).run();
    }

    public String getCircuitBreakerState(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        return cb.getState().name();
    }
}
