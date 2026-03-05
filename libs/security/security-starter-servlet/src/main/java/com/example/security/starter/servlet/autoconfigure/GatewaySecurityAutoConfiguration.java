package com.example.security.starter.servlet.autoconfigure;

import com.example.security.starter.servlet.client.GatewaySecurityClient;
import com.example.security.starter.servlet.config.GatewaySecurityWebMvcConfigurer;
import com.example.security.starter.servlet.filter.GatewaySecurityValidationFilter;
import com.example.security.starter.servlet.properties.GatewaySecurityModuleProperties;
import com.example.security.starter.servlet.verifier.GatewayRequestVerifier;
import com.example.security.signature.SignedHeaderParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "jakarta.servlet.Filter",
        "org.springframework.web.filter.OncePerRequestFilter"
})
@EnableConfigurationProperties(GatewaySecurityModuleProperties.class)
public class GatewaySecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GatewaySecurityClient.class)
    @ConditionalOnProperty(prefix = "app.gateway-security", name = "enabled", havingValue = "true")
    public GatewaySecurityClient gatewaySecurityClient(
            GatewaySecurityModuleProperties properties,
            ObjectProvider<SignedHeaderParser> signedHeaderParserProvider) {
        return new GatewayRequestVerifier(properties, signedHeaderParserProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(name = "gatewaySecurityValidationFilterRegistration")
    @ConditionalOnProperty(prefix = "app.gateway-security", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<GatewaySecurityValidationFilter> gatewaySecurityValidationFilterRegistration(
            GatewaySecurityClient securityClient,
            GatewaySecurityModuleProperties properties) {
        FilterRegistrationBean<GatewaySecurityValidationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new GatewaySecurityValidationFilter(securityClient, properties));
        registration.addUrlPatterns("/*");
        registration.setOrder(properties.getFilterOrder());
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(name = "gatewaySecurityWebMvcConfigurer")
    @ConditionalOnProperty(prefix = "app.gateway-security", name = "enabled", havingValue = "true")
    public WebMvcConfigurer gatewaySecurityWebMvcConfigurer() {
        return new GatewaySecurityWebMvcConfigurer();
    }
}
