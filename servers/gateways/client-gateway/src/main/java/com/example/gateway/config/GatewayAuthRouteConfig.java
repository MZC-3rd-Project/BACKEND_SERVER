package com.example.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayAuthRouteConfig {

    @Bean
    public RouteLocator authRouteLocator(RouteLocatorBuilder builder,
                                         @Value("${app.service.auth-url:http://localhost:8081}") String authServiceUrl,
                                         @Value("${app.service.user-url:http://localhost:8071}") String userServiceUrl) {
        return builder.routes()
                .route("auth-service-api", route -> route
                        .path("/api/v1/auth/**")
                        .uri(authServiceUrl))
                .route("profile-service-api", route -> route
                        .path("/api/profile", "/api/profile/**")
                        .uri(userServiceUrl))
                .route("profile-service-api-v1", route -> route
                        .path("/api/v1/users", "/api/v1/users/**", "/api/users", "/api/users/**")
                        .filters(filters -> filters.rewritePath(
                                "/api(?:/v1)?/users(?<segment>/?.*)",
                                "/api/profile${segment}"
                        ))
                        .uri(userServiceUrl))
                .build();
    }
}
