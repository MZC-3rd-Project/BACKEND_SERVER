package com.example.security.gateway;

import com.example.security.context.SecurityContextAutoConfiguration;
import com.example.security.signature.SecuritySignatureAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityContextAutoConfiguration.class,
                    SecuritySignatureAutoConfiguration.class,
                    GatewaySecurityAutoConfiguration.class
            ));

    @Test
    void shouldNotCreateGatewaySecurityBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GatewaySecurityClient.class);
            assertThat(context).doesNotHaveBean("gatewaySecurityValidationFilterRegistration");
        });
    }

    @Test
    void shouldCreateGatewaySecurityBeansWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.gateway-security.enabled=true",
                        "app.gateway-security.required-paths[0]=/api/v1/chat/**",
                        "app.security.context.signing-key=test-signing-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(GatewaySecurityClient.class);
                    assertThat(context).hasBean("gatewaySecurityValidationFilterRegistration");
                    FilterRegistrationBean<?> filterRegistrationBean =
                            context.getBean("gatewaySecurityValidationFilterRegistration", FilterRegistrationBean.class);
                    assertThat(filterRegistrationBean.getFilter()).isInstanceOf(GatewaySecurityValidationFilter.class);
                    assertThat(context).hasSingleBean(WebMvcConfigurer.class);
                });
    }
}
