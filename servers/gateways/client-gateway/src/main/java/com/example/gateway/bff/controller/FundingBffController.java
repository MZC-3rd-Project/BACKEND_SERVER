package com.example.gateway.bff.controller;

import com.example.gateway.bff.service.FundingBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1")
public class FundingBffController {

    private final FundingBffService fundingBffService;

    @GetMapping("/funding/campaigns")
    public Mono<ResponseEntity<JsonNode>> getCampaigns(ServerWebExchange exchange) {
        return fundingBffService.fetchCampaigns(exchange.getRequest().getURI());
    }

    @GetMapping("/funding/campaigns/{campaignId}")
    public Mono<ResponseEntity<JsonNode>> getCampaign(@PathVariable Long campaignId) {
        return fundingBffService.fetchCampaign(campaignId);
    }

    @GetMapping("/main/funding/closing-soon")
    public Mono<ResponseEntity<JsonNode>> getClosingSoon(ServerWebExchange exchange) {
        return fundingBffService.fetchClosingSoon(exchange.getRequest().getURI());
    }
}
