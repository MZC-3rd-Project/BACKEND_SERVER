package com.example.profile.client.config;

import com.example.profile.client.facade.DefaultProfileClient;
import com.example.profile.client.facade.ProfileItemQueryClientFacade;
import com.example.profile.client.facade.ProfileItemSummaryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class ProfileClientAutoConfiguration {

    // ✅ 이걸 추가
    @Bean
    @ConditionalOnMissingBean
    public DefaultProfileClient defaultProfileClient(
        WebClient.Builder webClientBuilder,
        @Value("${app.clients.profile.base-url:http://localhost:8071}") String profileServiceUrl
    ) {
        return new DefaultProfileClient(webClientBuilder, profileServiceUrl);
    }

    @Bean
    @ConditionalOnMissingBean(ProfileItemQueryClientFacade.class)
    public ProfileItemQueryClientFacade profileItemQueryClientFacade(DefaultProfileClient delegate){
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(ProfileItemSummaryClient.class)
    public ProfileItemSummaryClient profileItemSummaryClient(DefaultProfileClient delegate){
        return delegate;
    }
}
