package com.example.gateway.bff.controller;

import com.example.gateway.bff.service.SearchMediaBffService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class SearchMediaBffController {

    private final SearchMediaBffService searchMediaBffService;

    @GetMapping("/search")
    public Mono<ResponseEntity<JsonNode>> search(ServerHttpRequest serverHttpRequest) {
        return searchMediaBffService.search(serverHttpRequest);
    }

    @PostMapping("/search/clicks")
    public Mono<ResponseEntity<JsonNode>> trackClick(@RequestBody(required = false) JsonNode requestBody) {
        return searchMediaBffService.trackClick(requestBody);
    }
}
