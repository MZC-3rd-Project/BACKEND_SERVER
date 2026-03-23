package com.example.payment.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsClientConfig {

    @Bean
    public TossPaymentsClient tossPaymentsClient(
            TossPaymentsProperties properties,
            WebClient.Builder webClientBuilder
    ) {
        return new TossPaymentsWebClient(properties, webClientBuilder);
    }
}
