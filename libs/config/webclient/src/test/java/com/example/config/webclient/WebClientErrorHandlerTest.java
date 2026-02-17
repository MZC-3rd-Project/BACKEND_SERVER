package com.example.config.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientErrorHandlerTest {

    private final WebClientErrorHandler handler = new WebClientErrorHandler();

    @Test
    void filter_throwsWebClientResponseExceptionOn4xx() {
        ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("http://localhost/internal/v1/stock/reserve"))
                .build();
        ClientResponse response = ClientResponse.create(HttpStatus.CONFLICT)
                .body("{\"code\":\"STOCK-101\",\"message\":\"insufficient\"}")
                .build();
        ExchangeFunction next = ignored -> Mono.just(response);

        StepVerifier.create(handler.filter(request, next))
                .expectErrorSatisfies(error -> {
                    WebClientResponseException exception = assertInstanceOf(WebClientResponseException.class, error);
                    assertEquals(409, exception.getStatusCode().value());
                    assertTrue(exception.getResponseBodyAsString().contains("STOCK-101"));
                })
                .verify();
    }

    @Test
    void filter_passesResponseThroughOn2xx() {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost/internal/v1/items/1"))
                .build();
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .body("{\"success\":true}")
                .build();
        ExchangeFunction next = ignored -> Mono.just(response);

        StepVerifier.create(handler.filter(request, next))
                .assertNext(clientResponse -> assertEquals(200, clientResponse.statusCode().value()))
                .verifyComplete();
    }
}
