package com.example.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayAuthRouteConfig {

    @Bean
    public RouteLocator authRouteLocator(RouteLocatorBuilder builder, GatewayAuthProperties properties) {
        return builder.routes()
                .route("auth-service-api", route -> route
                        .path("/api/v1/auth/**")
                        .uri(properties.getAuthServiceUrl()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gateway.auth", name = "enabled", havingValue = "true")
    public RouteLocator userRouteLocator(RouteLocatorBuilder builder, GatewayAuthProperties properties) {
        return builder.routes()
                .route("profile-service-api", route -> route
                        .path("/api/profile", "/api/profile/**")
                        .uri(properties.getUserServiceUrl()))
                .route("profile-service-api-v1", route -> route
                        .path("/api/v1/users", "/api/v1/users/**", "/api/users", "/api/users/**")
                        .filters(filters -> filters.rewritePath(
                                "/api(?:/v1)?/users(?<segment>/?.*)",
                                "/api/profile${segment}"
                        ))
                        .uri(properties.getUserServiceUrl()))
                .build();
    }
}
