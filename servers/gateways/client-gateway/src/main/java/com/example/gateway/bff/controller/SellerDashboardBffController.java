package com.example.gateway.bff.controller;

import com.example.gateway.bff.service.SellerDashboardBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bff/v1/seller/dashboard")
@RequiredArgsConstructor
public class SellerDashboardBffController {

    private final SellerDashboardBffService sellerDashboardBffService;

    @GetMapping("/overview")
    public Mono<ResponseEntity<JsonNode>> getOverview(
            ServerHttpRequest request,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String timezone
    ) {
        return sellerDashboardBffService.getOverview(request, mode, date, yearMonth, from, to, bucket, timezone);
    }
}
