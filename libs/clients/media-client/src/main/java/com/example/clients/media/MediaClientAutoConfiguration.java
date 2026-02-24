package com.example.clients.media;

import com.example.config.resilience.CircuitBreakerHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class MediaClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MediaClientFacade.class)
    public MediaClientFacade mediaClientFacade(
            WebClient.Builder webClientBuilder,
            CircuitBreakerHelper circuitBreakerHelper,
            @Value("${app.clients.media.base-url:${app.service.media-url:http://localhost:8094}}") String mediaServiceUrl
    ) {
        return new DefaultMediaClientFacade(webClientBuilder, mediaServiceUrl, circuitBreakerHelper);
    }
}
