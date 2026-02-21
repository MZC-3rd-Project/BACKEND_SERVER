package com.example.security.signature;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(SecuritySignatureProperties.class)
public class SecuritySignatureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.security.context.signing-key:}')")
    public HmacSigner hmacSigner(SecuritySignatureProperties properties) {
        return new HmacSigner(properties.getSigningKey());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(HmacSigner.class)
    @ConditionalOnProperty(prefix = "app.security.context", name = "parser-enabled", havingValue = "true", matchIfMissing = true)
    public SignedHeaderParser signedHeaderParser(HmacSigner hmacSigner, SecuritySignatureProperties properties) {
        return new SignedHeaderParser(hmacSigner, properties.getMaxAgeMillis());
    }
}
