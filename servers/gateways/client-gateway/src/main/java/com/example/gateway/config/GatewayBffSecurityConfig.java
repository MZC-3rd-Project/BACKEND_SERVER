package com.example.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class GatewayBffSecurityConfig {

    @Bean
    @Order(0)
    @ConditionalOnProperty(prefix = "gateway.dev-login", name = "enabled", havingValue = "true")
    public SecurityWebFilterChain devLoginSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gateway.dev-login", name = "enabled", havingValue = "true")
    public MapReactiveUserDetailsService devLoginUserDetailsService(GatewayDevLoginProperties properties) {
        List<String> roles = properties.getRoles() == null ? List.of("USER") : properties.getRoles();
        String[] normalizedRoles = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .toArray(String[]::new);
        UserDetails user = User.withUsername(properties.getUsername())
                .password("{noop}" + properties.getPassword())
                .roles(normalizedRoles.length == 0 ? new String[]{"USER"} : normalizedRoles)
                .build();
        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(prefix = "gateway.auth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "devLoginSecurityWebFilterChain")
    public SecurityWebFilterChain oauth2SecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/profile", "/api/profile/**", "/api/v1/users/**", "/api/users/**")
                        .authenticated()
                        .anyExchange().permitAll())
                .oauth2Login(Customizer.withDefaults())
                .oauth2Client(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "gateway.auth", name = "enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "devLoginSecurityWebFilterChain")
    public SecurityWebFilterChain permitAllSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
