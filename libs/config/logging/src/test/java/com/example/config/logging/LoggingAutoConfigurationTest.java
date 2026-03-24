package com.example.config.logging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner reactiveContextRunner =
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class))
                    .withPropertyValues("app.logging.request-id-enabled=true");

    @Test
    void loadsReactiveLoggingWhenServletApiIsAbsent() {
        reactiveContextRunner
                .withClassLoader(new FilteredClassLoader("jakarta.servlet"))
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactiveRequestIdWebFilter.class);
                    assertThat(context).doesNotHaveBean("servletRequestIdMdcFilter");
                });
    }
}
