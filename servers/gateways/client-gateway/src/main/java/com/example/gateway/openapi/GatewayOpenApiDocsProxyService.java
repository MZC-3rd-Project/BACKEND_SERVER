package com.example.gateway.openapi;

import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GatewayOpenApiDocsProxyService {

    private static final String API_DOCS_PATH = "/v3/api-docs";

    private final WebClient.Builder webClientBuilder;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, String> docsBaseUrls;

    public GatewayOpenApiDocsProxyService(
            WebClient.Builder webClientBuilder,
            GatewaySecurityProperties securityProperties,
            ObjectMapper objectMapper,
            @Value("${app.service.auth-url:http://localhost:8081}") String authServiceUrl,
            @Value("${app.service.user-url:http://localhost:8071}") String profileServiceUrl,
            @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.service.stock-url:http://localhost:8085}") String stockServiceUrl,
            @Value("${app.service.funding-url:http://localhost:8086}") String fundingServiceUrl,
            @Value("${app.service.sales-url:http://localhost:8087}") String salesServiceUrl,
            @Value("${app.service.hot-deal-url:http://localhost:8089}") String hotDealServiceUrl,
            @Value("${app.service.order-url:http://localhost:8090}") String orderServiceUrl,
            @Value("${app.service.store-url:http://localhost:8072}") String storeServiceUrl,
            @Value("${app.service.store-query-url:http://localhost:8091}") String storeQueryServiceUrl,
            @Value("${app.service.notification-url:http://localhost:8092}") String notificationServiceUrl,
            @Value("${app.service.chat-url:http://localhost:8093}") String chatServiceUrl,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl,
            @Value("${app.service.analytics-dashboard-url:http://localhost:8095}") String analyticsDashboardServiceUrl,
            @Value("${app.service.cart-url:http://localhost:8096}") String cartServiceUrl
    ) {
        this.webClientBuilder = webClientBuilder;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.docsBaseUrls = Map.copyOf(buildDocsBaseUrls(
                authServiceUrl,
                profileServiceUrl,
                productServiceUrl,
                stockServiceUrl,
                fundingServiceUrl,
                salesServiceUrl,
                hotDealServiceUrl,
                orderServiceUrl,
                storeServiceUrl,
                storeQueryServiceUrl,
                notificationServiceUrl,
                chatServiceUrl,
                mediaServiceUrl,
                analyticsDashboardServiceUrl,
                cartServiceUrl
        ));
    }

    public Mono<ResponseEntity<JsonNode>> fetch(String serviceName, ServerHttpRequest request) {
        String baseUrl = docsBaseUrls.get(serviceName);
        if (!StringUtils.hasText(baseUrl)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundBody(serviceName)));
        }

        HttpHeaders downstreamHeaders = new HttpHeaders();
        downstreamHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            downstreamHeaders.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }

        return webClientBuilder.baseUrl(baseUrl)
                .build()
                .method(HttpMethod.GET)
                .uri(API_DOCS_PATH)
                .headers(headers -> headers.addAll(downstreamHeaders))
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(body -> ResponseEntity.status(response.statusCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(rewriteServers(body, externalBaseUrl(request)))));
    }

    private Map<String, String> buildDocsBaseUrls(
            String authServiceUrl,
            String profileServiceUrl,
            String productServiceUrl,
            String stockServiceUrl,
            String fundingServiceUrl,
            String salesServiceUrl,
            String hotDealServiceUrl,
            String orderServiceUrl,
            String storeServiceUrl,
            String storeQueryServiceUrl,
            String notificationServiceUrl,
            String chatServiceUrl,
            String mediaServiceUrl,
            String analyticsDashboardServiceUrl,
            String cartServiceUrl
    ) {
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("auth", authServiceUrl);
        urls.put("profile", profileServiceUrl);
        urls.put("product", productServiceUrl);
        urls.put("stock", stockServiceUrl);
        urls.put("funding", fundingServiceUrl);
        urls.put("sales", salesServiceUrl);
        urls.put("hot-deal", hotDealServiceUrl);
        urls.put("order", orderServiceUrl);
        urls.put("store", storeServiceUrl);
        urls.put("store-query", storeQueryServiceUrl);
        urls.put("notification", notificationServiceUrl);
        urls.put("chat", chatServiceUrl);
        urls.put("media-api", mediaServiceUrl);
        urls.put("analytics-dashboard", analyticsDashboardServiceUrl);
        urls.put("cart", cartServiceUrl);
        return urls;
    }

    private JsonNode rewriteServers(JsonNode originalBody, String serverUrl) {
        if (!(originalBody instanceof ObjectNode root)) {
            return originalBody;
        }

        ObjectNode copied = root.deepCopy();
        ArrayNode servers = copied.putArray("servers");
        if (StringUtils.hasText(serverUrl)) {
            servers.addObject()
                    .put("url", serverUrl)
                    .put("description", "Gateway public url");
        }
        return copied;
    }

    private String externalBaseUrl(ServerHttpRequest request) {
        if (request == null || request.getURI() == null) {
            return null;
        }
        String forwardedScheme = request.getHeaders().getFirst("X-Forwarded-Proto");
        String forwardedHost = request.getHeaders().getFirst("X-Forwarded-Host");
        String host = request.getHeaders().getFirst(HttpHeaders.HOST);

        String scheme = StringUtils.hasText(forwardedScheme) ? forwardedScheme : request.getURI().getScheme();
        String authority = StringUtils.hasText(forwardedHost)
                ? forwardedHost
                : (StringUtils.hasText(host) ? host : request.getURI().getAuthority());
        if (!StringUtils.hasText(scheme) || !StringUtils.hasText(authority)) {
            return null;
        }
        return scheme + "://" + authority;
    }

    private JsonNode notFoundBody(String serviceName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", false);
        body.putNull("data");
        body.putObject("error")
                .put("code", "GW-OPENAPI-404")
                .put("message", "지원하지 않는 OpenAPI 대상입니다: " + serviceName);
        return body;
    }
}
