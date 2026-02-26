package com.example.security.context;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "jakarta.servlet.Filter",
        "org.springframework.web.filter.OncePerRequestFilter"
})
@EnableConfigurationProperties(SecurityContextCleanupProperties.class)
public class SecurityContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "app.security.context",
            name = "cleanup-filter-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public AuthContextCleanupFilter authContextCleanupFilter() {
        return new AuthContextCleanupFilter();
    }
}
