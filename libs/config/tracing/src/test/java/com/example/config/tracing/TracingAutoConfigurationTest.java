package com.example.config.tracing;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TracingAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class))
            .withBean(Tracer.class, () -> mock(Tracer.class));

    @Test
    void registersMdcTracingFilterWhenEnabled() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(MdcTracingFilter.class));
    }

    @Test
    void doesNotRegisterTracingBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("app.tracing.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TracingProperties.class);
                    assertThat(context).doesNotHaveBean(MdcTracingFilter.class);
                });
    }
}
