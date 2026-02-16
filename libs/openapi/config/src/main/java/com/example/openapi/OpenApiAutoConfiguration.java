package com.example.openapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "app.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    @Bean
    public CommonHeaderOperationCustomizer commonHeaderOperationCustomizer() {
        return new CommonHeaderOperationCustomizer();
    }

    @Bean
    public GlobalOpenApiCustomizer globalOpenApiCustomizer(OpenApiProperties openApiProperties) {
        return new GlobalOpenApiCustomizer(openApiProperties);
    }
}
