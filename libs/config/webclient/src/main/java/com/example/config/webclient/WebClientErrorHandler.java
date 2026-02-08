package com.example.config.webclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WebClientErrorHandler implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(request)
                .flatMap(response -> {
                    HttpStatusCode statusCode = response.statusCode();

                    if (statusCode.is4xxClientError() || statusCode.is5xxServerError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    log.error("WebClient error response: {} {} - Status: {} - Body: {}",
                                            request.method(),
                                            request.url(),
                                            statusCode.value(),
                                            body);
                                    return Mono.error(new WebClientException(
                                            statusCode.value(),
                                            body
                                    ));
                                });
                    }

                    return Mono.just(response);
                });
    }
}
