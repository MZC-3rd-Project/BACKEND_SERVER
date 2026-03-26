package com.example.gateway.bff.controller;

import com.example.gateway.bff.service.OrderReadBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bff/v1/orders")
@RequiredArgsConstructor
public class OrderReadBffController {

    private final OrderReadBffService orderReadBffService;

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<JsonNode>> getOrderDetail(@PathVariable Long orderId) {
        return orderReadBffService.getOrderDetail(orderId);
    }
}
