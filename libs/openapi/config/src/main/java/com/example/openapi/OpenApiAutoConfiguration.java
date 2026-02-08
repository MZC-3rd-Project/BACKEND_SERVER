package com.example.openapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnProperty(prefix = "app.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
@ComponentScan(basePackages = "com.example.openapi")
public class OpenApiAutoConfiguration {
}
