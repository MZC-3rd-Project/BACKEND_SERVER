package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParseException;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.security.gateway.GatewayContextHeaderCodec;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class SearchClickRelayService {

    private static final String CODE_SEARCH_DISABLED = "BFF-SEARCH-503";
    private static final Duration CLICK_TRACK_TIMEOUT = Duration.ofMillis(500);

    private final WebClient searchWebClient;
    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;
    private final boolean searchEnabled;

    public SearchClickRelayService(
            WebClient.Builder webClientBuilder,
            GatewaySessionPrincipalResolver sessionPrincipalResolver,
            GatewaySecurityProperties securityProperties,
            ObjectMapper objectMapper,
            ObjectProvider<HmacSigner> hmacSignerProvider,
            @Value("${app.feature.search-enabled:true}") boolean searchEnabled,
            @Value("${app.service.search-url:http://localhost:8088}") String searchServiceUrl
    ) {
        this.searchWebClient = webClientBuilder.baseUrl(searchServiceUrl).build();
        this.sessionPrincipalResolver = sessionPrincipalResolver;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.hmacSignerProvider = hmacSignerProvider;
        this.searchEnabled = searchEnabled;
    }

    public Mono<ResponseEntity<JsonNode>> trackClick(JsonNode requestBody) {
        if (!searchEnabled) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(searchDisabledBody()));
        }

        JsonNode safeRequestBody = requestBody == null ? objectMapper.createObjectNode() : requestBody;
        return withOptionalUserContextHeaders(downstreamHeaders ->
                searchWebClient.post()
                        .uri("/api/v1/search/clicks")
                        .headers(headers -> headers.addAll(downstreamHeaders))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(safeRequestBody)
                        .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                                .defaultIfEmpty(objectMapper.createObjectNode())
                                .map(payload -> ResponseEntity.status(response.statusCode()).body(payload))));
    }

    public Mono<Void> trackClickBestEffort(Long itemId, String queryHash) {
        if (!searchEnabled || itemId == null || itemId <= 0L || !StringUtils.hasText(queryHash)) {
            return Mono.empty();
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("itemId", itemId);
        payload.put("queryHash", queryHash.trim());

        return trackClick(payload)
                .timeout(CLICK_TRACK_TIMEOUT)
                .doOnNext(response -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        log.debug("[SearchClickRelay] click tracking skipped. itemId={}, status={}",
                                itemId, response.getStatusCode());
                    }
                })
                .onErrorResume(error -> {
                    log.debug("[SearchClickRelay] click tracking failed. itemId={}", itemId, error);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<ResponseEntity<JsonNode>> withOptionalUserContextHeaders(
            Function<HttpHeaders, Mono<ResponseEntity<JsonNode>>> callback
    ) {
        return sessionPrincipalResolver.resolveFromSecurityContext()
                .map(Optional::of)
                .onErrorResume(SessionClaimParseException.class, error -> {
                    log.info("[SearchClickRelay] optional user context skipped. reason={}", error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalPrincipal -> callback.apply(buildDownstreamHeaders(optionalPrincipal.orElse(null))));
    }

    private HttpHeaders buildDownstreamHeaders(GatewaySessionPrincipal principal) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
        if (principal == null) {
            return headers;
        }

        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        headers.set(HttpHeaderNames.USER_ROLES, principal.rolesHeaderValue());
        if (StringUtils.hasText(principal.sessionId())) {
            headers.set(HttpHeaderNames.SESSION_ID, principal.sessionId());
        }

        String gatewayContext = createSignedContextHeader(principal);
        if (StringUtils.hasText(gatewayContext)) {
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext);
        }
        return headers;
    }

    private String createSignedContextHeader(GatewaySessionPrincipal principal) {
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null) {
            return null;
        }

        String userId = String.valueOf(principal.userId());
        String roles = principal.rolesHeaderValue();
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        return GatewayContextHeaderCodec.encodeSigned(userId, roles, nonce, timestamp, signer);
    }

    private JsonNode searchDisabledBody() {
        var body = objectMapper.createObjectNode();
        body.put("success", false);
        body.putNull("data");
        body.putObject("error")
                .put("code", CODE_SEARCH_DISABLED)
                .put("message", "검색 기능이 비활성화되었습니다");
        return body;
    }
}
