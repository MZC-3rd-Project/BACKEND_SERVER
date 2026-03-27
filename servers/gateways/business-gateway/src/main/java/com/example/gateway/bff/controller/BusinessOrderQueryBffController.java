package com.example.gateway.bff.controller;

import com.example.gateway.bff.service.BusinessOrderQueryBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bff/v1/orders")
@RequiredArgsConstructor
public class BusinessOrderQueryBffController {

    private final BusinessOrderQueryBffService businessOrderQueryBffService;

    @GetMapping
    public Mono<ResponseEntity<JsonNode>> getMyOrders(@RequestParam(required = false) String status) {
        return businessOrderQueryBffService.getMyOrders(status);
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<JsonNode>> getOrderDetail(@PathVariable Long orderId) {
        return businessOrderQueryBffService.getOrderDetail(orderId);
    }
}
