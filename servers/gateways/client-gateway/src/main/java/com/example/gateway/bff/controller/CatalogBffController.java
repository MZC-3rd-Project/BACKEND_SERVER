package com.example.gateway.bff.controller;

import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import com.example.gateway.bff.service.CatalogDetailBffService;
import com.example.gateway.bff.service.CatalogBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class CatalogBffController {

    private final CatalogBffService catalogBffService;
    private final CatalogDetailBffService catalogDetailBffService;

    @GetMapping("/catalog/items")
    public Mono<ResponseEntity<CatalogItemsResponse>> listCatalogItems(ServerHttpRequest request) {
        return catalogBffService.listCatalogItems(request);
    }

    @GetMapping("/catalog/items/{itemId}/detail")
    public Mono<ResponseEntity<JsonNode>> getCatalogDetail(@PathVariable Long itemId,
                                                           @RequestParam String itemType,
                                                           @RequestParam String salesChannel,
                                                           @RequestParam(required = false) Long hotDealId,
                                                           @RequestParam(required = false) Long campaignId) {
        return catalogDetailBffService.getCatalogDetail(itemId, itemType, salesChannel, hotDealId, campaignId);
    }
}
