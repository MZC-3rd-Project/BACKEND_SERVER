package com.example.gateway.config;

import com.example.security.context.HmacSigner;
import com.example.security.context.SecurityContextProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(SecurityContextProperties.class)
public class GatewaySecurityContextConfig {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.security.context", name = "signing-key")
    public HmacSigner hmacSigner(SecurityContextProperties properties) {
        if (!StringUtils.hasText(properties.getSigningKey())) {
            throw new IllegalStateException("app.security.context.signing-key must not be blank");
        }
        return new HmacSigner(properties.getSigningKey());
    }
}
