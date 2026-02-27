package com.example.gateway.bff.controller;

import com.example.gateway.bff.service.CommerceReadBffService;
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
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class CommerceReadBffController {

    private final CommerceReadBffService commerceReadBffService;

    @GetMapping("/funding/campaigns")
    public Mono<ResponseEntity<JsonNode>> findFundingCampaigns(ServerHttpRequest request) {
        return commerceReadBffService.findFundingCampaigns(request);
    }

    @GetMapping("/funding/campaigns/{campaignId}")
    public Mono<ResponseEntity<JsonNode>> findFundingCampaignDetail(@PathVariable Long campaignId) {
        return commerceReadBffService.findFundingCampaignDetail(campaignId);
    }

    @GetMapping("/hot-deals")
    public Mono<ResponseEntity<JsonNode>> findHotDeals(ServerHttpRequest request) {
        return commerceReadBffService.findHotDeals(request);
    }

    @GetMapping("/hot-deals/{hotDealId}")
    public Mono<ResponseEntity<JsonNode>> findHotDealDetail(@PathVariable Long hotDealId) {
        return commerceReadBffService.findHotDealDetail(hotDealId);
    }

    @GetMapping("/sales/products")
    public Mono<ResponseEntity<JsonNode>> findSalesProducts(ServerHttpRequest request) {
        return commerceReadBffService.findSalesProducts(request);
    }

    @GetMapping("/sales/products/{saleId}")
    public Mono<ResponseEntity<JsonNode>> findSalesProductDetail(@PathVariable Long saleId) {
        return commerceReadBffService.findSalesProductDetail(saleId);
    }
}
