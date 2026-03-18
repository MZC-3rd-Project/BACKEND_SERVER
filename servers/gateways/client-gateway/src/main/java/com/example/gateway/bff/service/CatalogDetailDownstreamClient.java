package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CatalogDetailDownstreamClient {

    Mono<ResponseEntity<JsonNode>> fetchHotDealDetail(Long hotDealId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchFundingDetail(Long campaignId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchFundingDetailByItem(Long itemId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchNormalDetail(BffItemType itemType, Long itemId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchItemSummary(Long itemId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchFundingParticipations(Long campaignId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchStockSummary(Long itemId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchStoreDetail(Long storeId, HttpHeaders headers);

    Mono<ResponseEntity<JsonNode>> fetchMediaUrls(List<Long> mediaIds, HttpHeaders headers);
}
