package com.example.search.config;

import com.example.security.context.SignedHeaderParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(SearchSecurityProperties.class)
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<GatewayHeaderValidationFilter> gatewayHeaderValidationFilter(
            SearchSecurityProperties properties,
            ObjectProvider<SignedHeaderParser> signedHeaderParserProvider) {
        FilterRegistrationBean<GatewayHeaderValidationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new GatewayHeaderValidationFilter(properties, signedHeaderParserProvider.getIfAvailable()));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
