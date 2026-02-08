package com.example.config.webclient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(WebClientProperties.class)
public class WebClientAutoConfiguration {

    private final WebClientProperties properties;
    private final WebClientLoggingFilter loggingFilter;
    private final WebClientErrorHandler errorHandler;

    @PostConstruct
    public void init() {
        log.info("WebClientAutoConfiguration initialized with properties: " +
                        "connectTimeout={}ms, readTimeout={}ms, writeTimeout={}ms, maxInMemorySize={}bytes",
                properties.getConnectTimeout(),
                properties.getReadTimeout(),
                properties.getWriteTimeout(),
                properties.getMaxInMemorySize());
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(properties.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.getWriteTimeout(), TimeUnit.MILLISECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(properties.getMaxInMemorySize()))
                .filter(loggingFilter)
                .filter(errorHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        log.info("Creating default WebClient bean");
        return webClientBuilder.build();
    }
}
