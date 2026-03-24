package com.example.payment.client;

import com.example.config.resilience.CircuitBreakerHelper;
import com.example.payment.client.dto.TossCancelRequest;
import com.example.payment.client.dto.TossCancelResponse;
import com.example.payment.client.dto.TossConfirmRequest;
import com.example.payment.client.dto.TossConfirmResponse;

import java.util.function.Supplier;
import com.example.payment.client.dto.TossPaymentResponse;
import com.example.payment.exception.TossPaymentsApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Base64;

@Slf4j
public class TossPaymentsWebClient implements TossPaymentsClient {

    private static final String CB_CONFIRM = "toss-payments-confirm";
    private static final String CB_CANCEL = "toss-payments-cancel";

    private final WebClient webClient;
    private final Duration confirmTimeout;
    private final CircuitBreakerHelper circuitBreakerHelper;

    public TossPaymentsWebClient(TossPaymentsProperties properties, WebClient.Builder webClientBuilder,
                                  CircuitBreakerHelper circuitBreakerHelper) {
        this.circuitBreakerHelper = circuitBreakerHelper;

        String encodedKey = Base64.getEncoder().encodeToString(
                (properties.getSecretKey() + ":").getBytes()
        );

        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.confirmTimeout = Duration.ofSeconds(properties.getConfirmTimeoutSeconds());
    }

    @Override
    public TossConfirmResponse confirm(TossConfirmRequest request) {
        Supplier<TossConfirmResponse> supplier = () -> {
            try {
                return webClient.post()
                        .uri("/v1/payments/confirm")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(TossConfirmResponse.class)
                        .block(confirmTimeout);
            } catch (WebClientResponseException e) {
                log.error("토스페이먼츠 결제 승인 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new TossPaymentsApiException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            }
        };
        return circuitBreakerHelper.executeWithCircuitBreakerAndRetry(CB_CONFIRM, supplier);
    }

    @Override
    public TossCancelResponse cancel(String paymentKey, TossCancelRequest request) {
        Supplier<TossCancelResponse> supplier = () -> {
            try {
                return webClient.post()
                        .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(TossCancelResponse.class)
                        .block(confirmTimeout);
            } catch (WebClientResponseException e) {
                log.error("토스페이먼츠 결제 취소 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new TossPaymentsApiException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            }
        };
        return circuitBreakerHelper.executeWithCircuitBreakerAndRetry(CB_CANCEL, supplier);
    }

    @Override
    public TossPaymentResponse query(String paymentKey) {
        try {
            return webClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .bodyToMono(TossPaymentResponse.class)
                    .block(confirmTimeout);
        } catch (WebClientResponseException e) {
            log.error("토스페이먼츠 결제 조회 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new TossPaymentsApiException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }
}
