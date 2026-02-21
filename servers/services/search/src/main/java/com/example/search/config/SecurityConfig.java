package com.example.search.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(SearchPopularProperties.class)
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<GatewayHeaderValidationFilter> gatewayHeaderValidationFilter() {
        FilterRegistrationBean<GatewayHeaderValidationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new GatewayHeaderValidationFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
