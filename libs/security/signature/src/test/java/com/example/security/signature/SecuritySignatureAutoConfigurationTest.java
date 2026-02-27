package com.example.security.signature;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritySignatureAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecuritySignatureAutoConfiguration.class));

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
    void shouldNotCreateSignerWhenSigningKeyIsBlank() {
        contextRunner
                .withPropertyValues("app.security.context.signing-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(HmacSigner.class);
                    assertThat(context).doesNotHaveBean(SignedHeaderParser.class);
                });
    }

    @Test
    void shouldNotCreateParserWhenParserDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.security.context.signing-key=test-signing-key",
                        "app.security.context.parser-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HmacSigner.class);
                    assertThat(context).doesNotHaveBean(SignedHeaderParser.class);
                });
    }
}
