package com.example.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenApiAutoConfiguration.class));

    @Test
    void registersGlobalCustomizerByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenApiProperties.class);
            assertThat(context).hasSingleBean(GlobalOpenApiCustomizer.class);
            assertThat(context).doesNotHaveBean(CommonHeaderOperationCustomizer.class);
        });
    }

    @Test
    void registersHeaderCustomizerWhenEnabled() {
        contextRunner
                .withPropertyValues("app.openapi.expose-gateway-headers=true")
                .run(context -> assertThat(context).hasSingleBean(CommonHeaderOperationCustomizer.class));
    }

    @Test
    void disablesOpenApiModuleWhenTurnedOff() {
        contextRunner
                .withPropertyValues("app.openapi.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenApiProperties.class);
                    assertThat(context).doesNotHaveBean(GlobalOpenApiCustomizer.class);
                    assertThat(context).doesNotHaveBean(CommonHeaderOperationCustomizer.class);
                });
    }
}
