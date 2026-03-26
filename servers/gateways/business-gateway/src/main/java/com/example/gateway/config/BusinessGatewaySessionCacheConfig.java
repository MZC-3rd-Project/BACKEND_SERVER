package com.example.gateway.config;

import com.example.gateway.security.session.domain.GatewayServerSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class BusinessGatewaySessionCacheConfig {

    @Bean
    @ConditionalOnProperty(prefix = "gateway.session", name = "cache-enabled", havingValue = "true", matchIfMissing = true)
    public Cache<String, GatewayServerSession> gatewaySessionCache(BusinessGatewaySessionProperties sessionProperties) {
        return Caffeine.newBuilder()
                .maximumSize(sessionProperties.getCacheMaximumSize())
                .expireAfterWrite(Duration.ofSeconds(sessionProperties.getCacheExpireAfterWriteSeconds()))
                .build();
    }
}
