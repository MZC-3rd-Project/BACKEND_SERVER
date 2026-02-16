package com.example.security.context;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@EnableConfigurationProperties(SecurityContextProperties.class)
public class SecurityContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnProperty(
            prefix = "app.security.context",
            name = "cleanup-filter-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public AuthContextCleanupFilter authContextCleanupFilter() {
        return new AuthContextCleanupFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.security.context", name = "signing-key")
    public HmacSigner hmacSigner(SecurityContextProperties properties) {
        if (!StringUtils.hasText(properties.getSigningKey())) {
            throw new IllegalStateException(
                    "app.security.context.signing-key must not be blank when security context signing is enabled"
            );
        }
        return new HmacSigner(properties.getSigningKey());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(HmacSigner.class)
    public SignedHeaderParser signedHeaderParser(HmacSigner hmacSigner, SecurityContextProperties properties) {
        return new SignedHeaderParser(hmacSigner, properties.getMaxAgeMillis());
    }
}
