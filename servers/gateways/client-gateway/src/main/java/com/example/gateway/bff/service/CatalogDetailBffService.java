package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.example.gateway.bff.dto.catalog.CatalogSalesChannel;
import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CatalogDetailBffService {

    private static final String CODE_INVALID_REQUEST = "BFF-CATALOG-400";
    private static final String CODE_DOWNSTREAM_ERROR = "BFF-CATALOG-502";

    private final CatalogDetailDownstreamClient downstreamClient;
    private final GatewaySecurityProperties securityProperties;
    private final CatalogMetricsService catalogMetricsService;
    private final ObjectMapper objectMapper;

    public CatalogDetailBffService(CatalogDetailDownstreamClient downstreamClient,
                                   GatewaySecurityProperties securityProperties,
                                   CatalogMetricsService catalogMetricsService,
                                   ObjectMapper objectMapper) {
        this.downstreamClient = downstreamClient;
        this.securityProperties = securityProperties;
        this.catalogMetricsService = catalogMetricsService;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<JsonNode>> getCatalogDetail(Long itemId,
                                                           String itemTypeValue,
                                                           String salesChannelValue,
                                                           Long hotDealId,
                                                           Long campaignId) {
        CatalogDetailRequest request;
        try {
            request = normalize(itemId, itemTypeValue, salesChannelValue, hotDealId, campaignId);
        } catch (IllegalArgumentException e) {
            return Mono.just(badRequest(e.getMessage()));
        }

        HttpHeaders downstreamHeaders = buildDownstreamHeaders();
        return routePrimary(request, downstreamHeaders)
                .onErrorResume(e -> {
                    log.warn("[CatalogDetail] route failed. itemId={}, channel={}", request.itemId(), request.salesChannel(), e);
                    return Mono.just(badGateway("상세 조회에 실패했습니다"));
                });
    }

    private Mono<ResponseEntity<JsonNode>> routePrimary(CatalogDetailRequest request, HttpHeaders headers) {
        return switch (request.salesChannel()) {
            case HOT_DEAL -> routeHotDeal(request, headers);
            case FUNDING -> routeFunding(request, headers);
            case NORMAL, ALL -> downstreamClient.fetchNormalDetail(request.itemType(), request.itemId(), headers);
        };
    }

    private Mono<ResponseEntity<JsonNode>> routeHotDeal(CatalogDetailRequest request, HttpHeaders headers) {
        if (request.hotDealId() == null) {
            return fallbackToNormal(request, headers, "hot_deal_id_missing");
        }
        return downstreamClient.fetchHotDealDetail(request.hotDealId(), headers)
                .flatMap(primary -> apply404Fallback(request, headers, primary, "hot_deal_404"));
    }

    private Mono<ResponseEntity<JsonNode>> routeFunding(CatalogDetailRequest request, HttpHeaders headers) {
        Mono<ResponseEntity<JsonNode>> primaryMono = request.campaignId() != null
                ? downstreamClient.fetchFundingDetail(request.campaignId(), headers)
                : downstreamClient.fetchFundingDetailByItem(request.itemId(), headers);

        String fallbackReason = request.campaignId() != null
                ? "funding_campaign_404"
                : "funding_item_404";

        return primaryMono.flatMap(primary -> apply404Fallback(request, headers, primary, fallbackReason));
    }

    private Mono<ResponseEntity<JsonNode>> apply404Fallback(CatalogDetailRequest request,
                                                            HttpHeaders headers,
                                                            ResponseEntity<JsonNode> primaryResponse,
                                                            String reason) {
        if (primaryResponse.getStatusCode() != HttpStatus.NOT_FOUND) {
            return Mono.just(primaryResponse);
        }
        return fallbackToNormal(request, headers, reason);
    }

    private Mono<ResponseEntity<JsonNode>> fallbackToNormal(CatalogDetailRequest request,
                                                            HttpHeaders headers,
                                                            String reason) {
        catalogMetricsService.recordDetailFallback(request.salesChannel().name(), reason);
        log.info("[CatalogDetail] fallback applied. itemId={}, salesChannel={}, reason={}",
                request.itemId(), request.salesChannel(), reason);
        return downstreamClient.fetchNormalDetail(request.itemType(), request.itemId(), headers)
                .onErrorResume(e -> {
                    log.warn("[CatalogDetail] fallback route failed. itemId={}, salesChannel={}, reason={}",
                            request.itemId(), request.salesChannel(), reason, e);
                    return Mono.just(badGateway("fallback 상세 조회에 실패했습니다"));
                });
    }

    private CatalogDetailRequest normalize(Long itemId,
                                           String itemTypeValue,
                                           String salesChannelValue,
                                           Long hotDealId,
                                           Long campaignId) {
        if (itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("itemId는 양수여야 합니다");
        }
        if (hotDealId != null && hotDealId <= 0) {
            throw new IllegalArgumentException("hotDealId는 양수여야 합니다");
        }
        if (campaignId != null && campaignId <= 0) {
            throw new IllegalArgumentException("campaignId는 양수여야 합니다");
        }

        BffItemType itemType = parseItemType(itemTypeValue);
        CatalogSalesChannel salesChannel = parseSalesChannel(salesChannelValue);
        return new CatalogDetailRequest(itemId, itemType, salesChannel, hotDealId, campaignId);
    }

    private BffItemType parseItemType(String itemTypeValue) {
        if (!StringUtils.hasText(itemTypeValue)) {
            throw new IllegalArgumentException("itemType은 필수입니다");
        }
        try {
            return BffItemType.fromNullable(itemTypeValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("itemType은 PRODUCT, GOODS, PERFORMANCE 중 하나여야 합니다");
        }
    }

    private CatalogSalesChannel parseSalesChannel(String salesChannelValue) {
        if (!StringUtils.hasText(salesChannelValue)) {
            throw new IllegalArgumentException("salesChannel은 필수입니다");
        }
        CatalogSalesChannel channel = CatalogSalesChannel.fromNullable(salesChannelValue);
        if (channel == CatalogSalesChannel.ALL) {
            return CatalogSalesChannel.NORMAL;
        }
        return channel;
    }

    private HttpHeaders buildDownstreamHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
        return headers;
    }

    private ResponseEntity<JsonNode> badRequest(String message) {
        return ResponseEntity.badRequest().body(errorPayload(CODE_INVALID_REQUEST, message));
    }

    private ResponseEntity<JsonNode> badGateway(String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorPayload(CODE_DOWNSTREAM_ERROR, message));
    }

    private ObjectNode errorPayload(String code, String message) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", false);
        ObjectNode error = body.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return body;
    }

    private record CatalogDetailRequest(Long itemId,
                                        BffItemType itemType,
                                        CatalogSalesChannel salesChannel,
                                        Long hotDealId,
                                        Long campaignId) {
    }
}
