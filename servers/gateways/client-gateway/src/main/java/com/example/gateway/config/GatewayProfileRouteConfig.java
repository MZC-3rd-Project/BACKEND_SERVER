package com.example.gateway.config;

import com.example.contracts.http.HttpHeaderNames;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayProfileRouteConfig {

    private final GatewayAuthProperties authProperties;
    private final GatewayProfileRouteProperties profileRouteProperties;

    @Bean
    @ConditionalOnProperty(prefix = "gateway.profile-route", name = "enabled", havingValue = "true")
    public RouteLocator profileRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("profile-service-api", route -> route
                        .path("/api/profile", "/api/profile/**")
                        .filters(filters -> {
                            if (profileRouteProperties.getDummyUserId() != null) {
                                filters.setRequestHeader(
                                        HttpHeaderNames.USER_ID,
                                        String.valueOf(profileRouteProperties.getDummyUserId())
                                );
                            }
                            return filters;
                        })
                        .uri(authProperties.getUserServiceUrl()))
                .build();
    }
}
