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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

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
                .flatMap(primaryResponse -> {
                    if (primaryResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return fallbackToNormal(request, headers, "hot_deal_404");
                    }
                    return enrichHotDealResponse(request, headers, primaryResponse);
                });
    }

    private Mono<ResponseEntity<JsonNode>> routeFunding(CatalogDetailRequest request, HttpHeaders headers) {
        Mono<ResponseEntity<JsonNode>> primaryMono = request.campaignId() != null
                ? downstreamClient.fetchFundingDetail(request.campaignId(), headers)
                : downstreamClient.fetchFundingDetailByItem(request.itemId(), headers);

        String fallbackReason = request.campaignId() != null
                ? "funding_campaign_404"
                : "funding_item_404";

        return primaryMono
                .flatMap(primaryResponse -> {
                    if (primaryResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return fallbackToNormal(request, headers, fallbackReason);
                    }
                    return enrichFundingResponse(request, headers, primaryResponse);
                });
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

    private Mono<ResponseEntity<JsonNode>> enrichHotDealResponse(CatalogDetailRequest request,
                                                                 HttpHeaders headers,
                                                                 ResponseEntity<JsonNode> response) {
        ObjectNode dataNode = dataObject(response);
        if (dataNode == null) {
            return Mono.just(response);
        }

        String leftLabel = toLeftLabel(textOrNull(dataNode.path("endAt")));
        if (StringUtils.hasText(leftLabel) && !StringUtils.hasText(textOrNull(dataNode.path("leftLabel")))) {
            dataNode.put("leftLabel", leftLabel);
        }

        Long itemId = positiveLong(dataNode.path("itemId"), request.itemId());
        if (itemId == null) {
            return Mono.just(response);
        }

        return enrichWithNormalItem(request.itemType(), itemId, headers, response, dataNode);
    }

    private Mono<ResponseEntity<JsonNode>> enrichFundingResponse(CatalogDetailRequest request,
                                                                 HttpHeaders headers,
                                                                 ResponseEntity<JsonNode> response) {
        ObjectNode dataNode = dataObject(response);
        if (dataNode == null) {
            return Mono.just(response);
        }

        enrichProgressRate(dataNode);

        Long itemId = positiveLong(dataNode.path("itemId"), request.itemId());
        Long campaignId = positiveLong(dataNode.path("id"), request.campaignId());

        Mono<ResponseEntity<JsonNode>> chain = Mono.just(response);
        if (itemId != null) {
            chain = chain.flatMap(r -> enrichWithNormalItem(request.itemType(), itemId, headers, r, dataNode));
        }
        if (campaignId != null) {
            chain = chain.flatMap(r -> enrichSupporterCount(campaignId, headers, r, dataNode));
        }
        return chain;
    }

    private Mono<ResponseEntity<JsonNode>> enrichWithNormalItem(BffItemType itemType,
                                                                Long itemId,
                                                                HttpHeaders headers,
                                                                ResponseEntity<JsonNode> response,
                                                                ObjectNode dataNode) {
        return downstreamClient.fetchNormalDetail(itemType, itemId, headers)
                .map(normalResponse -> {
                    ObjectNode itemData = dataObject(normalResponse);
                    if (itemData != null) {
                        mergeItemData(dataNode, itemData);
                    }
                    return response;
                })
                .flatMap(r -> enrichThumbnailUrl(headers, r, dataNode))
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] normal item enrichment skipped. itemId={}", itemId, e);
                    return Mono.just(response);
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichSupporterCount(Long campaignId,
                                                                HttpHeaders headers,
                                                                ResponseEntity<JsonNode> response,
                                                                ObjectNode dataNode) {
        return downstreamClient.fetchFundingParticipations(campaignId, headers)
                .map(participationResponse -> {
                    ObjectNode participationData = dataObject(participationResponse);
                    if (participationData != null) {
                        JsonNode items = participationData.path("items");
                        if (items.isArray()) {
                            dataNode.put("supporterCount", items.size());
                            return response;
                        }
                    }

                    JsonNode body = participationResponse.getBody();
                    if (participationResponse.getStatusCode().is2xxSuccessful()
                            && body != null
                            && body.path("success").asBoolean(false)) {
                        JsonNode data = body.path("data");
                        if (data.isArray()) {
                            dataNode.put("supporterCount", data.size());
                        }
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] supporterCount enrichment skipped. campaignId={}", campaignId, e);
                    return Mono.just(response);
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichThumbnailUrl(HttpHeaders headers,
                                                              ResponseEntity<JsonNode> response,
                                                              ObjectNode dataNode) {
        if (StringUtils.hasText(textOrNull(dataNode.path("thumbnailUrl")))) {
            return Mono.just(response);
        }
        Long thumbnailMediaId = positiveLong(dataNode.path("thumbnailMediaId"), null);
        if (thumbnailMediaId == null) {
            return Mono.just(response);
        }

        return downstreamClient.fetchMediaUrls(List.of(thumbnailMediaId), headers)
                .map(mediaResponse -> {
                    JsonNode body = mediaResponse.getBody();
                    if (mediaResponse.getStatusCode().is2xxSuccessful()
                            && body != null
                            && body.path("success").asBoolean(false)) {
                        JsonNode data = body.path("data");
                        if (data.isArray()) {
                            for (JsonNode node : data) {
                                if (thumbnailMediaId.equals(positiveLong(node.path("mediaId"), null))) {
                                    String mediaUrl = textOrNull(node.path("mediaUrl"));
                                    if (StringUtils.hasText(mediaUrl)) {
                                        dataNode.put("thumbnailUrl", mediaUrl);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] thumbnailUrl enrichment skipped. thumbnailMediaId={}", thumbnailMediaId, e);
                    return Mono.just(response);
                });
    }

    private void mergeItemData(ObjectNode target, ObjectNode itemData) {
        putIfBlank(target, "title", textOrNull(itemData.path("title")));
        putIfNull(target, "price", positiveLong(itemData.path("price"), null));
        putIfBlank(target, "status", textOrNull(itemData.path("status")));
        putIfNull(target, "categoryId", positiveLong(itemData.path("categoryId"), null));

        JsonNode images = itemData.path("images");
        if (images.isObject()) {
            JsonNode thumbnail = images.path("thumbnail");
            Long mediaId = positiveLong(thumbnail.path("mediaId"), null);
            putIfNull(target, "thumbnailMediaId", mediaId);
        }

        if (!target.has("item")) {
            target.set("item", itemData.deepCopy());
        }
    }

    private void enrichProgressRate(ObjectNode dataNode) {
        if (dataNode.hasNonNull("progressRate")) {
            return;
        }
        Long currentAmount = positiveLong(dataNode.path("currentAmount"), 0L);
        Long goalAmount = positiveLong(dataNode.path("goalAmount"), 0L);
        if (goalAmount == null || goalAmount <= 0) {
            dataNode.put("progressRate", 0.0);
            return;
        }
        double rate = (double) currentAmount / goalAmount * 100.0;
        double rounded = Math.round(rate * 100) / 100.0;
        dataNode.put("progressRate", rounded);
    }

    private ObjectNode dataObject(ResponseEntity<JsonNode> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            return null;
        }
        JsonNode body = response.getBody();
        if (body == null || !body.isObject() || !body.path("success").asBoolean(false)) {
            return null;
        }
        JsonNode data = body.path("data");
        if (!data.isObject()) {
            return null;
        }
        return (ObjectNode) data;
    }

    private String toLeftLabel(String rawEndAt) {
        if (!StringUtils.hasText(rawEndAt)) {
            return null;
        }
        Instant endAt = toInstant(rawEndAt);
        if (endAt == null) {
            return null;
        }

        Duration duration = Duration.between(Instant.now(), endAt);
        if (duration.isNegative() || duration.isZero()) {
            return "종료";
        }

        long seconds = duration.getSeconds();
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainSeconds = seconds % 60;

        if (days > 0) {
            return days + "일 " + hours + "시간 남음";
        }
        if (hours > 0) {
            return hours + "시간 " + minutes + "분 남음";
        }
        return String.format("%02d:%02d:%02d", hours, minutes, remainSeconds);
    }

    private Instant toInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(value);
                return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private Long positiveLong(JsonNode node, Long fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        Long parsed;
        if (node.isNumber()) {
            parsed = node.asLong();
        } else if (node.isTextual()) {
            String raw = node.asText(null);
            if (!StringUtils.hasText(raw)) {
                return fallback;
            }
            try {
                parsed = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        } else {
            return fallback;
        }
        return parsed >= 0 ? parsed : fallback;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void putIfBlank(ObjectNode node, String fieldName, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!StringUtils.hasText(textOrNull(node.path(fieldName)))) {
            node.put(fieldName, value);
        }
    }

    private void putIfNull(ObjectNode node, String fieldName, Long value) {
        if (value == null) {
            return;
        }
        if (!node.hasNonNull(fieldName)) {
            node.put(fieldName, value);
        }
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
