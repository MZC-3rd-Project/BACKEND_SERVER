package com.example.config.webclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WebClientLoggingFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long startTime = System.currentTimeMillis();

        log.info("WebClient Request: {} {}", request.method(), request.url());

        return next.exchange(request)
                .doOnNext(response -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.info("WebClient Response: {} {} - Status: {} - Elapsed: {}ms",
                            request.method(),
                            request.url(),
                            response.statusCode().value(),
                            elapsedTime);
                })
                .doOnError(error -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.error("WebClient Error: {} {} - Elapsed: {}ms - Error: {}",
                            request.method(),
                            request.url(),
                            elapsedTime,
                            error.getMessage());
                });
    }
}
