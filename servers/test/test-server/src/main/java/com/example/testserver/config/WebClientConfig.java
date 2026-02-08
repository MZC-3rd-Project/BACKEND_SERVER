package com.example.testserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(@Value("${server.port:8083}") int port) {
        return WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }
}
