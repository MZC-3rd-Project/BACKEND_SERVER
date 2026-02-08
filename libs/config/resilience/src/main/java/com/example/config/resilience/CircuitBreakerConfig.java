package com.example.config.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig defaultConfig =
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowType(SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(80)
                        .slowCallDurationThreshold(Duration.ofSeconds(3))
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    CircuitBreaker cb = event.getAddedEntry();
                    cb.getEventPublisher()
                            .onStateTransition(e -> log.warn("[CircuitBreaker] {} state: {} → {}",
                                    cb.getName(), e.getStateTransition().getFromState(),
                                    e.getStateTransition().getToState()))
                            .onError(e -> log.debug("[CircuitBreaker] {} error: {}",
                                    cb.getName(), e.getThrowable().getMessage()));
                });

        return registry;
    }
}
