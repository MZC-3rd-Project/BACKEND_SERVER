package com.example.gateway.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v3/api-docs-proxy")
@RequiredArgsConstructor
public class GatewayOpenApiDocsController {

    private final GatewayOpenApiDocsProxyService gatewayOpenApiDocsProxyService;

    @GetMapping("/{serviceName}")
    public Mono<ResponseEntity<JsonNode>> proxyDownstreamDocs(@PathVariable String serviceName,
                                                              ServerHttpRequest request) {
        return gatewayOpenApiDocsProxyService.fetch(serviceName, request);
    }
}
