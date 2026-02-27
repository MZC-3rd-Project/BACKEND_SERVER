package com.example.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayAuthRouteConfig {

    @Bean
    @ConditionalOnProperty(prefix = "gateway.auth", name = "enabled", havingValue = "true")
    public RouteLocator authRouteLocator(RouteLocatorBuilder builder, GatewayAuthProperties properties) {
        return builder.routes()
                .route("auth-service-api", route -> route
                        .path("/api/v1/auth/**")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri(properties.getAuthServiceUrl()))
                .route("user-service-api-v1", route -> route
                        .path("/api/v1/users/**")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri(properties.getUserServiceUrl()))
                .route("user-service-api-legacy", route -> route
                        .path("/api/users/**")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri(properties.getUserServiceUrl()))
                .build();
    }
}
