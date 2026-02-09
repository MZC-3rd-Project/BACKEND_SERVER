package com.example.config.webclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@Component
public class WebClientLoggingFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long startTime = System.currentTimeMillis();
        String sanitizedUrl = sanitizeUrl(request.url());

        log.info("WebClient Request: {} {}", request.method(), sanitizedUrl);

        return next.exchange(request)
                .doOnNext(response -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.info("WebClient Response: {} {} - Status: {} - Elapsed: {}ms",
                            request.method(),
                            sanitizedUrl,
                            response.statusCode().value(),
                            elapsedTime);
                })
                .doOnError(error -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.error("WebClient Error: {} {} - Elapsed: {}ms - Error: {}",
                            request.method(),
                            sanitizedUrl,
                            elapsedTime,
                            error.getMessage());
                });
    }

    private String sanitizeUrl(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return uri.toString();
        }
        return uri.getScheme() + "://" + uri.getAuthority() + uri.getPath() + "?[REDACTED]";
    }
}
