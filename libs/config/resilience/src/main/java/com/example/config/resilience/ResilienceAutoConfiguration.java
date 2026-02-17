package com.example.config.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({CircuitBreakerConfig.class, RetryConfig.class})
public class ResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FallbackRegistry fallbackRegistry() {
        return new FallbackRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerHelper circuitBreakerHelper(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            FallbackRegistry fallbackRegistry
    ) {
        return new CircuitBreakerHelper(circuitBreakerRegistry, retryRegistry, fallbackRegistry);
    }
}
