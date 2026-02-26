package com.example.clients.auth.config;

import com.example.clients.auth.facade.AuthItemQueryClientFacade;
import com.example.clients.auth.facade.AuthItemSummaryClientFacade;
import com.example.clients.auth.facade.DefaultAuthClientFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
public class AuthClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DefaultAuthClientFacade.class)
    public DefaultAuthClientFacade defaultAuthClientFacade(
            WebClient.Builder webClientBuilder,
            @Value("${app.clients.auth.base-url:${app.service.auth-url:http://localhost:8081}}") String baseUrl
    ) {
        return new DefaultAuthClientFacade(webClientBuilder, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(AuthItemSummaryClientFacade.class)
    public AuthItemSummaryClientFacade authItemSummaryClientFacade(DefaultAuthClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(AuthItemQueryClientFacade.class)
    public AuthItemQueryClientFacade authItemQueryClientFacade(DefaultAuthClientFacade delegate) {
        return delegate;
    }
}
