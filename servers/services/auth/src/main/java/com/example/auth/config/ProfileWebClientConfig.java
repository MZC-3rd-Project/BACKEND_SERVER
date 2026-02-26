package com.example.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ProfileWebClientConfig {

    @Bean
    public WebClient profileWebClient(
            WebClient.Builder webClientBuilder,
            @Value("${profile.service.base-url}") String profileServiceUrl
    ) {
        return webClientBuilder
                .baseUrl(profileServiceUrl)
                .build();
    }


}
