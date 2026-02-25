package com.example.gateway.bff.controller;

import com.example.gateway.bff.dto.BffItemCreateCommandRequest;
import com.example.gateway.bff.dto.BffItemUpdateCommandRequest;
import com.example.gateway.bff.service.ProductMediaBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public Mono<ResponseEntity<JsonNode>> createProduct(@RequestBody BffItemCreateCommandRequest request) {
        return productMediaBffService.createProductWithMedia(request);
    }

    @PutMapping("/products/{itemId}")
    public Mono<ResponseEntity<JsonNode>> updateProduct(@PathVariable Long itemId,
                                                        @RequestBody BffItemUpdateCommandRequest request) {
        return productMediaBffService.updateProductWithMedia(itemId, request);
    }

    @PostMapping("/goods")
    public Mono<ResponseEntity<JsonNode>> createGoods(@RequestBody BffItemCreateCommandRequest request) {
        return productMediaBffService.createGoodsWithMedia(request);
    }

    @PutMapping("/goods/{itemId}")
    public Mono<ResponseEntity<JsonNode>> updateGoods(@PathVariable Long itemId,
                                                      @RequestBody BffItemUpdateCommandRequest request) {
        return productMediaBffService.updateGoodsWithMedia(itemId, request);
    }

    @PostMapping("/performances")
    public Mono<ResponseEntity<JsonNode>> createPerformance(@RequestBody BffItemCreateCommandRequest request) {
        return productMediaBffService.createPerformanceWithMedia(request);
    }

    @PutMapping("/performances/{itemId}")
    public Mono<ResponseEntity<JsonNode>> updatePerformance(@PathVariable Long itemId,
                                                            @RequestBody BffItemUpdateCommandRequest request) {
        return productMediaBffService.updatePerformanceWithMedia(itemId, request);
    }

    @GetMapping("/items/{itemId}")
    public Mono<ResponseEntity<JsonNode>> findItemDetail(@RequestParam String type,
                                                         @PathVariable Long itemId) {
        return productMediaBffService.findItemDetail(type, itemId);
    }

    @GetMapping("/items")
    public Mono<ResponseEntity<JsonNode>> findItemList(@RequestParam String type,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) Integer size) {
        return productMediaBffService.findItemList(type, cursor, size);
    }
}
