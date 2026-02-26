package com.example.security.context;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityContextAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityContextAutoConfiguration.class));

    @Test
    void shouldCreateCleanupFilterByDefault() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(AuthContextCleanupFilter.class)
        );
    }

    @Test
    void shouldDisableCleanupFilterWhenPropertyOff() {
        contextRunner
                .withPropertyValues("app.security.context.cleanup-filter-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuthContextCleanupFilter.class);
                });
    }
}
