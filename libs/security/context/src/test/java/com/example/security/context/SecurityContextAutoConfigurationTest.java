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
    void shouldCreateSignerAndParserWhenSigningKeyExists() {
        contextRunner
                .withPropertyValues("app.security.context.signing-key=test-signing-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(HmacSigner.class);
                    assertThat(context).hasSingleBean(SignedHeaderParser.class);
                });
    }

    @Test
    void shouldFailFastWhenSigningKeyIsBlank() {
        contextRunner
                .withPropertyValues("app.security.context.signing-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("must not be blank");
                });
    }
}
