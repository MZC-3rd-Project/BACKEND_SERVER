package com.example.config.resilience;

import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Configuration
public class RetryConfig {

    @Bean
    public RetryRegistry retryRegistry() {
        io.github.resilience4j.retry.RetryConfig defaultConfig =
                io.github.resilience4j.retry.RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(500))
                        .retryExceptions(IOException.class, TimeoutException.class)
                        .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    event.getAddedEntry().getEventPublisher()
                            .onRetry(e -> log.warn("[Retry] {} attempt #{}: {}",
                                    e.getName(), e.getNumberOfRetryAttempts(),
                                    e.getLastThrowable().getMessage()))
                            .onError(e -> log.error("[Retry] {} failed after {} attempts",
                                    e.getName(), e.getNumberOfRetryAttempts()));
                });

        return registry;
    }
}
