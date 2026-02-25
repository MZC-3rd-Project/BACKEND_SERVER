package com.example.gateway.bff.controller;

import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import com.example.gateway.bff.service.CatalogBffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class CatalogBffController {

    private final CatalogBffService catalogBffService;

    @GetMapping("/catalog/items")
    public Mono<ResponseEntity<CatalogItemsResponse>> listCatalogItems(ServerHttpRequest request) {
        return catalogBffService.listCatalogItems(request);
    }
}
