package com.example.clients.profile.config;

import com.example.clients.profile.facade.ProfileClientFacade;
import com.example.clients.profile.impl.DefaultProfileClientFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class ProfileClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DefaultProfileClientFacade defaultProfileClientFacade(
            WebClient.Builder webClientBuilder,
            @Value("${app.clients.profile.base-url:${app.service.profile-url:http://localhost:8071}}") String profileServiceUrl
    ) {
        return new DefaultProfileClientFacade(webClientBuilder, profileServiceUrl);
    }

    @Bean
    @ConditionalOnMissingBean(ProfileClientFacade.class)
    public ProfileClientFacade profileClientFacade(DefaultProfileClientFacade delegate) {
        return delegate;
    }
}
