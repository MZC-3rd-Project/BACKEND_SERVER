package com.example.config.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TracingAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner reactiveContextRunner =
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class))
                    .withPropertyValues("app.tracing.enabled=true");

    @Test
    void loadsReactiveContextWhenServletApiIsAbsent() {
        reactiveContextRunner
                .withClassLoader(new FilteredClassLoader("jakarta.servlet"))
                .run(context -> assertThat(context).doesNotHaveBean("mdcTracingFilter"));
    }
}
