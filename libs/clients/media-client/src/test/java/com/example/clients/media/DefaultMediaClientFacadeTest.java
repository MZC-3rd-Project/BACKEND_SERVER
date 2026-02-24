package com.example.clients.media;

import com.example.config.resilience.CircuitBreakerHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DefaultMediaClientFacadeTest {

    @Mock
    private CircuitBreakerHelper circuitBreakerHelper;

    @BeforeEach
    void setUp() {
        lenient().when(circuitBreakerHelper.executeWithCircuitBreakerAndRetry(anyString(), any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<?> callable = invocation.getArgument(1);
                    return callable.call();
                });
    }

    @Test
    void getMediaUrl_returnsUrl_whenResponseIsReady() {
        DefaultMediaClientFacade facade = facadeForResponse(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("""
                        {"success":true,"data":{"mediaId":10,"status":"READY","mediaUrl":"https://cdn.example.com/a.webp"}}
                        """)
                .build());

        String mediaUrl = facade.getMediaUrl(10L);

        assertThat(mediaUrl).isEqualTo("https://cdn.example.com/a.webp");
    }

    @Test
    void getMediaUrl_throwsInvalidMediaReference_whenResponseIs4xx() {
        DefaultMediaClientFacade facade = facadeForResponse(ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{" + "\"success\":false" + "}")
                .build());

        assertThatThrownBy(() -> facade.getMediaUrl(10L))
                .isInstanceOf(InvalidMediaReferenceException.class);
    }

    @Test
    void getMediaUrl_throwsRetriableException_whenResponseIs5xx() {
        DefaultMediaClientFacade facade = facadeForResponse(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("{" + "\"success\":false" + "}")
                .build());

        assertThatThrownBy(() -> facade.getMediaUrl(10L))
                .isInstanceOf(MediaClientRetriableException.class);
    }

    @Test
    void getMediaUrl_throwsRetriableException_whenNetworkTimeoutOccurs() {
        WebClientRequestException networkError = new WebClientRequestException(
                new IOException("timeout"),
                HttpMethod.GET,
                URI.create("http://localhost/internal/v1/media/10/url"),
                HttpHeaders.EMPTY
        );
        DefaultMediaClientFacade facade = facadeForError(networkError);

        assertThatThrownBy(() -> facade.getMediaUrl(10L))
                .isInstanceOf(MediaClientRetriableException.class);
    }

    @Test
    void getMediaUrlMap_returnsMap_whenBatchResponseIsValid() {
        DefaultMediaClientFacade facade = facadeForResponse(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("""
                        {
                          "success": true,
                          "data": [
                            {"mediaId": 10, "mediaUrl": "https://cdn.example.com/10.webp"},
                            {"mediaId": 11, "mediaUrl": "https://cdn.example.com/11.webp"}
                          ]
                        }
                        """)
                .build());

        Map<Long, String> mediaUrlMap = facade.getMediaUrlMap(java.util.List.of(10L, 11L, 11L));

        assertThat(mediaUrlMap)
                .containsEntry(10L, "https://cdn.example.com/10.webp")
                .containsEntry(11L, "https://cdn.example.com/11.webp");
    }

    private DefaultMediaClientFacade facadeForResponse(ClientResponse response) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response))
                .build();
        return new DefaultMediaClientFacade(webClient, circuitBreakerHelper);
    }

    private DefaultMediaClientFacade facadeForError(Throwable throwable) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(throwable))
                .build();
        return new DefaultMediaClientFacade(webClient, circuitBreakerHelper);
    }
}
