package com.example.search.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(SearchPopularProperties.class)
public class SearchPropertiesConfig {

    @Bean
    public FilterRegistrationBean<SearchRequestAuditFilter> searchRequestAuditFilterRegistration() {
        FilterRegistrationBean<SearchRequestAuditFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SearchRequestAuditFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
