package com.example.gateway.bff.controller;

import com.example.gateway.bff.dto.BffItemCreateCommandRequest;
import com.example.gateway.bff.dto.BffItemUpdateCommandRequest;
import com.example.gateway.bff.service.ProductMediaBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class ProductMediaBffController {

    private final ProductMediaBffService productMediaBffService;

    @PostMapping("/products")
    public Mono<ResponseEntity<JsonNode>> createProduct(@RequestBody BffItemCreateCommandRequest request,
                                                        ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.createProductWithMedia(request, serverHttpRequest.getHeaders());
    }

    @PutMapping("/products/{itemId}")
    public Mono<ResponseEntity<JsonNode>> updateProduct(@PathVariable Long itemId,
                                                        @RequestBody BffItemUpdateCommandRequest request,
                                                        ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.updateProductWithMedia(itemId, request, serverHttpRequest.getHeaders());
    }

    @PostMapping("/goods")
    public Mono<ResponseEntity<JsonNode>> createGoods(@RequestBody BffItemCreateCommandRequest request,
                                                      ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.createGoodsWithMedia(request, serverHttpRequest.getHeaders());
    }

    @PutMapping("/goods/{itemId}")
    public Mono<ResponseEntity<JsonNode>> updateGoods(@PathVariable Long itemId,
                                                      @RequestBody BffItemUpdateCommandRequest request,
                                                      ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.updateGoodsWithMedia(itemId, request, serverHttpRequest.getHeaders());
    }

    @PostMapping("/performances")
    public Mono<ResponseEntity<JsonNode>> createPerformance(@RequestBody BffItemCreateCommandRequest request,
                                                            ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.createPerformanceWithMedia(request, serverHttpRequest.getHeaders());
    }

    @PutMapping("/performances/{itemId}")
    public Mono<ResponseEntity<JsonNode>> updatePerformance(@PathVariable Long itemId,
                                                            @RequestBody BffItemUpdateCommandRequest request,
                                                            ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.updatePerformanceWithMedia(itemId, request, serverHttpRequest.getHeaders());
    }

    @GetMapping("/items/{itemId}")
    public Mono<ResponseEntity<JsonNode>> findItemDetail(@RequestParam String type,
                                                         @PathVariable Long itemId,
                                                         ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.findItemDetail(type, itemId, serverHttpRequest.getHeaders());
    }

    @GetMapping("/items")
    public Mono<ResponseEntity<JsonNode>> findItemList(@RequestParam String type,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) Integer size,
                                                       ServerHttpRequest serverHttpRequest) {
        return productMediaBffService.findItemList(type, cursor, size, serverHttpRequest.getHeaders());
    }
}
