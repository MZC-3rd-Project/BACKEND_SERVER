package com.example.config.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@AutoConfiguration
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(
        prefix = "app.logging",
        name = "request-id-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LoggingAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class ServletLoggingConfiguration {

        @Bean
        public ServletRequestIdMdcFilter servletRequestIdMdcFilter(LoggingProperties properties) {
            return new ServletRequestIdMdcFilter(properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFilter.class)
    static class ReactiveLoggingConfiguration {

        @Bean
        public ReactiveRequestIdWebFilter reactiveRequestIdWebFilter(LoggingProperties properties) {
            return new ReactiveRequestIdWebFilter(properties);
        }
    }
}
