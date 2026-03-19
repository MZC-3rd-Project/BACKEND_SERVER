package com.example.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.WebFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class GatewayBffCorsConfig {

    private static final String BFF_PATH_PREFIX = "/bff/";

    private final CorsConfiguration corsConfiguration;

    public GatewayBffCorsConfig(
            @Value("${GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS:*}") String allowedOriginPatterns
    ) {
        this.corsConfiguration = new CorsConfiguration();
        this.corsConfiguration.setAllowCredentials(false);
        this.corsConfiguration.addAllowedHeader(CorsConfiguration.ALL);
        this.corsConfiguration.addAllowedMethod(CorsConfiguration.ALL);
        this.corsConfiguration.setMaxAge(3600L);
        parseAllowedOriginPatterns(allowedOriginPatterns)
                .forEach(this.corsConfiguration::addAllowedOriginPattern);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter gatewayBffCorsWebFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().pathWithinApplication().value();
            String origin = exchange.getRequest().getHeaders().getOrigin();
            if (!path.startsWith(BFF_PATH_PREFIX) || !StringUtils.hasText(origin)) {
                return chain.filter(exchange);
            }

            String allowedOrigin = corsConfiguration.checkOrigin(origin);
            if (!StringUtils.hasText(allowedOrigin)) {
                if (isPreflightRequest(exchange.getRequest().getMethod(), exchange.getRequest().getHeaders())) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
                return chain.filter(exchange);
            }

            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
            headers.add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
            headers.add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
            headers.setAccessControlAllowOrigin(allowedOrigin);
            headers.setAccessControlAllowMethods(List.of(
                    HttpMethod.GET,
                    HttpMethod.POST,
                    HttpMethod.PUT,
                    HttpMethod.PATCH,
                    HttpMethod.DELETE,
                    HttpMethod.OPTIONS,
                    HttpMethod.HEAD
            ));
            headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, CorsConfiguration.ALL);
            headers.setAccessControlMaxAge(corsConfiguration.getMaxAge());

            if (isPreflightRequest(exchange.getRequest().getMethod(), exchange.getRequest().getHeaders())) {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }

    private static List<String> parseAllowedOriginPatterns(String allowedOriginPatterns) {
        return Arrays.stream(StringUtils.commaDelimitedListToStringArray(allowedOriginPatterns))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static boolean isPreflightRequest(HttpMethod method, HttpHeaders headers) {
        return HttpMethod.OPTIONS.equals(method)
                && StringUtils.hasText(headers.getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD));
    }
}
