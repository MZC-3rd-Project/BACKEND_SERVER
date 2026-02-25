package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface CatalogDetailDownstreamClient {

    Mono<ResponseEntity<JsonNode>> fetchHotDealDetail(Long hotDealId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchFundingDetail(Long campaignId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchFundingDetailByItem(Long itemId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchNormalDetail(BffItemType itemType, Long itemId, HttpHeaders headers);
}
