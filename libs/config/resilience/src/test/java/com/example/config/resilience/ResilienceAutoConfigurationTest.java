package com.example.config.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResilienceAutoConfiguration.class));

    @Test
    void registersResilienceCoreBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
            assertThat(context).hasSingleBean(RetryRegistry.class);
            assertThat(context).hasSingleBean(FallbackRegistry.class);
            assertThat(context).hasSingleBean(CircuitBreakerHelper.class);
        });
    }
}
