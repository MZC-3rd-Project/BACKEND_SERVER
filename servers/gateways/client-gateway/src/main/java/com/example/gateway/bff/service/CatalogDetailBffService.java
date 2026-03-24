package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.example.gateway.bff.dto.catalog.CatalogSalesChannel;
import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    private static final String CHECKOUT_ENTRY_TYPE = "SALES_CHECKOUT";

    private final CatalogDetailDownstreamClient downstreamClient;
    private final SearchClickRelayService searchClickRelayService;
    private final GatewaySecurityProperties securityProperties;
    private final CatalogMetricsService catalogMetricsService;
    private final ObjectMapper objectMapper;

    public CatalogDetailBffService(CatalogDetailDownstreamClient downstreamClient,
                                   SearchClickRelayService searchClickRelayService,
                                   GatewaySecurityProperties securityProperties,
                                   CatalogMetricsService catalogMetricsService,
                                   ObjectMapper objectMapper) {
        this.downstreamClient = downstreamClient;
        this.searchClickRelayService = searchClickRelayService;
        this.securityProperties = securityProperties;
        this.catalogMetricsService = catalogMetricsService;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<JsonNode>> getCatalogDetail(Long itemId,
                                                           String itemTypeValue,
                                                           String salesChannelValue,
                                                           Long hotDealId,
                                                           Long campaignId,
                                                           String searchQueryHash) {
        CatalogDetailRequest request;
        try {
            request = normalize(itemId, itemTypeValue, salesChannelValue, hotDealId, campaignId);
        } catch (IllegalArgumentException e) {
            return Mono.just(badRequest(e.getMessage()));
        }

        HttpHeaders downstreamHeaders = buildDownstreamHeaders();
        Mono<ResponseEntity<JsonNode>> detailMono = routePrimary(request, downstreamHeaders)
                .onErrorResume(e -> {
                    log.warn("[CatalogDetail] route failed. itemId={}, channel={}", request.itemId(), request.salesChannel(), e);
                    return Mono.just(badGateway("상세 조회에 실패했습니다"));
                })
                .map(this::normalizeSnowflakeIds);
        return detailMono.zipWith(
                searchClickRelayService.trackClickBestEffort(request.itemId(), searchQueryHash).thenReturn(Boolean.TRUE),
                (response, ignored) -> response
        );
    }

    public Mono<ResponseEntity<JsonNode>> getFundingCampaignDetail(Long campaignId) {
        if (campaignId == null || campaignId <= 0) {
            return Mono.just(badRequest("campaignId는 양수여야 합니다"));
        }

        HttpHeaders downstreamHeaders = buildDownstreamHeaders();
        CatalogDetailRequest request = new CatalogDetailRequest(null, null, CatalogSalesChannel.FUNDING, null, campaignId);
        return downstreamClient.fetchFundingDetail(campaignId, downstreamHeaders)
                .flatMap(response -> response.getStatusCode().is2xxSuccessful()
                        ? enrichFundingResponse(request, downstreamHeaders, response)
                        : Mono.just(response))
                .onErrorResume(e -> {
                    log.warn("[CatalogDetail] funding detail failed. campaignId={}", campaignId, e);
                    return Mono.just(badGateway("펀딩 상세 조회에 실패했습니다"));
                })
                .map(this::normalizeSnowflakeIds);
    }

    private Mono<ResponseEntity<JsonNode>> routePrimary(CatalogDetailRequest request, HttpHeaders headers) {
        return switch (request.salesChannel()) {
            case HOT_DEAL -> routeHotDeal(request, headers);
            case FUNDING -> routeFunding(request, headers);
            case NORMAL, ALL -> downstreamClient.fetchNormalDetail(request.itemType(), request.itemId(), headers)
                    .flatMap(response -> enrichNormalResponse(request, headers, response));
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
        if (request.itemType() == null || request.itemId() == null) {
            return Mono.just(badGateway("fallback 상세 조회에 실패했습니다"));
        }

        catalogMetricsService.recordDetailFallback(request.salesChannel().name(), reason);
        log.info("[CatalogDetail] fallback applied. itemId={}, salesChannel={}, reason={}",
                request.itemId(), request.salesChannel(), reason);
        return downstreamClient.fetchNormalDetail(request.itemType(), request.itemId(), headers)
                .flatMap(response -> enrichNormalResponse(
                        new CatalogDetailRequest(request.itemId(), request.itemType(), CatalogSalesChannel.NORMAL, null, request.campaignId()),
                        headers,
                        response
                ))
                .onErrorResume(e -> {
                    log.warn("[CatalogDetail] fallback route failed. itemId={}, salesChannel={}, reason={}",
                            request.itemId(), request.salesChannel(), reason, e);
                    return Mono.just(badGateway("fallback 상세 조회에 실패했습니다"));
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichNormalResponse(CatalogDetailRequest request,
                                                                HttpHeaders headers,
                                                                ResponseEntity<JsonNode> response) {
        ObjectNode dataNode = dataObject(response);
        if (dataNode == null) {
            return Mono.just(response);
        }

        ensureBaseIdentifiers(dataNode, request);
        ensureItemPayloadFromCurrentData(dataNode);
        return enrichCommonReadFields(request, headers, response, dataNode);
    }

    private Mono<ResponseEntity<JsonNode>> enrichHotDealResponse(CatalogDetailRequest request,
                                                                 HttpHeaders headers,
                                                                 ResponseEntity<JsonNode> response) {
        ObjectNode dataNode = dataObject(response);
        if (dataNode == null) {
            return Mono.just(response);
        }

        ensureBaseIdentifiers(dataNode, request);

        String leftLabel = toLeftLabel(textOrNull(dataNode.path("endAt")));
        if (StringUtils.hasText(leftLabel) && !StringUtils.hasText(textOrNull(dataNode.path("leftLabel")))) {
            dataNode.put("leftLabel", leftLabel);
        }

        return enrichCommonReadFields(request, headers, response, dataNode);
    }

    private Mono<ResponseEntity<JsonNode>> enrichFundingResponse(CatalogDetailRequest request,
                                                                 HttpHeaders headers,
                                                                 ResponseEntity<JsonNode> response) {
        ObjectNode dataNode = dataObject(response);
        if (dataNode == null) {
            return Mono.just(response);
        }

        ensureBaseIdentifiers(dataNode, request);
        enrichProgressRate(dataNode);

        Long campaignId = positiveLong(dataNode.path("campaignId"),
                positiveLong(dataNode.path("id"), request.campaignId()));

        Mono<ResponseEntity<JsonNode>> chain = Mono.just(response);
        if (campaignId != null) {
            chain = chain.flatMap(r -> enrichSupporterCount(campaignId, headers, r, dataNode));
        }
        return chain.flatMap(r -> enrichCommonReadFields(request, headers, r, dataNode));
    }

    private Mono<ResponseEntity<JsonNode>> enrichCommonReadFields(CatalogDetailRequest request,
                                                                  HttpHeaders headers,
                                                                  ResponseEntity<JsonNode> response,
                                                                  ObjectNode dataNode) {
        Long itemId = resolveItemId(dataNode, request.itemId());
        if (itemId == null) {
            finalizeDetailPayload(request, dataNode);
            return Mono.just(response);
        }

        Mono<ResponseEntity<JsonNode>> chain = Mono.just(response)
                .flatMap(r -> enrichWithItemSummary(itemId, headers, r, dataNode))
                .flatMap(r -> ensureItemPayload(request, itemId, headers, r, dataNode))
                .flatMap(r -> enrichReviews(itemId, headers, r, dataNode))
                .flatMap(r -> enrichStore(headers, r, dataNode))
                .flatMap(r -> enrichStock(itemId, headers, r, dataNode));

        if (request.salesChannel() == CatalogSalesChannel.NORMAL) {
            chain = chain.flatMap(r -> enrichOriginFunding(itemId, headers, r, dataNode));
        }

        return chain
                .map(r -> {
                    enrichRewardOptions(dataNode, request.salesChannel());
                    return r;
                })
                .flatMap(r -> enrichThumbnailUrl(headers, r, dataNode))
                .map(r -> {
                    finalizeDetailPayload(request, dataNode);
                    return r;
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichWithItemSummary(Long itemId,
                                                                 HttpHeaders headers,
                                                                 ResponseEntity<JsonNode> response,
                                                                 ObjectNode dataNode) {
        if (hasSufficientItemSummary(dataNode)) {
            return Mono.just(response);
        }

        return downstreamClient.fetchItemSummary(itemId, headers)
                .map(summaryResponse -> {
                    ObjectNode summary = dataObject(summaryResponse);
                    if (summary != null) {
                        mergeItemSummary(dataNode, summary);
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] item summary enrichment skipped. itemId={}", itemId, e);
                    return Mono.just(response);
                });
    }

    private boolean hasSufficientItemSummary(ObjectNode dataNode) {
        return StringUtils.hasText(textOrNull(dataNode.path("itemType")))
                && positiveLong(dataNode.path("storeId"), null) != null
                && resolveThumbnailMediaId(dataNode) != null;
    }

    private Mono<ResponseEntity<JsonNode>> ensureItemPayload(CatalogDetailRequest request,
                                                             Long itemId,
                                                             HttpHeaders headers,
                                                             ResponseEntity<JsonNode> response,
                                                             ObjectNode dataNode) {
        if (dataNode.has("item")) {
            return Mono.just(response);
        }
        if (request.salesChannel() == CatalogSalesChannel.NORMAL) {
            ensureItemPayloadFromCurrentData(dataNode);
            return Mono.just(response);
        }

        BffItemType itemType = resolveItemType(dataNode, request.itemType());
        if (itemType == null) {
            return Mono.just(response);
        }

        return downstreamClient.fetchNormalDetail(itemType, itemId, headers)
                .map(normalResponse -> {
                    ObjectNode itemData = dataObject(normalResponse);
                    if (itemData != null) {
                        mergeItemData(dataNode, itemData);
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] normal item enrichment skipped. itemId={}", itemId, e);
                    return Mono.just(response);
                });
    }

    private void ensureItemPayloadFromCurrentData(ObjectNode dataNode) {
        if (dataNode.has("item")) {
            return;
        }
        dataNode.set("item", dataNode.deepCopy());
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

    private Mono<ResponseEntity<JsonNode>> enrichStore(HttpHeaders headers,
                                                       ResponseEntity<JsonNode> response,
                                                       ObjectNode dataNode) {
        Long storeId = resolveStoreId(dataNode);
        if (storeId == null) {
            return Mono.just(response);
        }

        JsonNode currentStore = dataNode.path("store");
        if (currentStore.isObject()
                && StringUtils.hasText(textOrNull(currentStore.path("name")))) {
            return Mono.just(response);
        }

        return downstreamClient.fetchStoreDetail(storeId, headers)
                .map(storeResponse -> {
                    ObjectNode storeData = dataObject(storeResponse);
                    if (storeData != null) {
                        ObjectNode store = objectMapper.createObjectNode();
                        store.put("id", storeId);
                        putIfBlank(store, "name", textOrNull(storeData.path("storeName")));
                        putIfBlank(store, "tagline", textOrNull(storeData.path("description")));
                        dataNode.set("store", store);
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] store enrichment skipped. storeId={}", storeId, e);
                    return Mono.just(response);
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichStock(Long itemId,
                                                       HttpHeaders headers,
                                                       ResponseEntity<JsonNode> response,
                                                       ObjectNode dataNode) {
        return downstreamClient.fetchStockSummary(itemId, headers)
                .map(stockResponse -> {
                    ObjectNode stockData = dataObject(stockResponse);
                    if (stockData != null) {
                        dataNode.set("stock", toStockPayload(stockData));
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] stock enrichment skipped. itemId={}", itemId, e);
                    return Mono.just(response);
                });
    }

    private ObjectNode toStockPayload(ObjectNode stockData) {
        ObjectNode stock = objectMapper.createObjectNode();
        putNullableInt(stock, "availableQuantity", intOrNull(stockData.path("availableQuantity")));
        putNullableInt(stock, "soldQuantity", intOrNull(stockData.path("soldQuantity")));
        putNullableBoolean(stock, "soldOut", booleanOrNull(stockData.path("soldOut")));
        ArrayNode optionStocks = stock.putArray("optionStocks");

        JsonNode optionStocksNode = stockData.path("optionStocks");
        if (optionStocksNode.isArray()) {
            for (JsonNode node : optionStocksNode) {
                if (!node.isObject()) {
                    continue;
                }
                Long itemOptionId = positiveLong(node.path("itemOptionId"), null);
                if (itemOptionId == null) {
                    continue;
                }
                ObjectNode optionStock = optionStocks.addObject();
                optionStock.put("itemOptionId", itemOptionId);
                putNullableInt(optionStock, "availableQuantity", intOrNull(node.path("availableQuantity")));
                putNullableBoolean(optionStock, "soldOut", booleanOrNull(node.path("soldOut")));
            }
        }

        if (stock.path("availableQuantity").isNull()
                || stock.path("soldQuantity").isNull()
                || stock.path("soldOut").isNull()
                || optionStocks.isEmpty()) {
            fillStockPayloadFallback(stockData, stock, optionStocks);
        }
        return stock;
    }

    private void fillStockPayloadFallback(ObjectNode stockData, ObjectNode stock, ArrayNode optionStocks) {
        JsonNode stocksNode = stockData.path("stocks");
        if (!stocksNode.isArray()) {
            return;
        }

        int totalQuantity = 0;
        int availableQuantity = 0;
        int reservedQuantity = 0;
        boolean hasAnyStock = false;
        boolean everySoldOut = true;
        boolean populateFallbackOptionStocks = optionStocks.isEmpty();

        for (JsonNode node : stocksNode) {
            if (!node.isObject()) {
                continue;
            }
            hasAnyStock = true;
            int total = node.path("totalQuantity").asInt(0);
            int available = node.path("availableQuantity").asInt(0);
            int reserved = node.path("reservedQuantity").asInt(0);
            totalQuantity += total;
            availableQuantity += available;
            reservedQuantity += reserved;
            everySoldOut = everySoldOut && available <= 0;

            if (populateFallbackOptionStocks && "ITEM_OPTION".equalsIgnoreCase(textOrNull(node.path("stockItemType")))) {
                Long itemOptionId = positiveLong(node.path("referenceId"), null);
                if (itemOptionId == null) {
                    continue;
                }
                ObjectNode optionStock = optionStocks.addObject();
                optionStock.put("itemOptionId", itemOptionId);
                optionStock.put("availableQuantity", available);
                optionStock.put("soldOut", available <= 0);
            }
        }

        if (!hasAnyStock) {
            return;
        }
        if (stock.path("availableQuantity").isNull()) {
            stock.put("availableQuantity", availableQuantity);
        }
        if (stock.path("soldQuantity").isNull()) {
            stock.put("soldQuantity", totalQuantity - availableQuantity - reservedQuantity);
        }
        if (stock.path("soldOut").isNull()) {
            stock.put("soldOut", everySoldOut);
        }
    }

    private Mono<ResponseEntity<JsonNode>> enrichOriginFunding(Long itemId,
                                                               HttpHeaders headers,
                                                               ResponseEntity<JsonNode> response,
                                                               ObjectNode dataNode) {
        if (dataNode.hasNonNull("originFunding")) {
            return Mono.just(response);
        }

        return downstreamClient.fetchFundingDetailByItem(itemId, headers)
                .map(fundingResponse -> {
                    ObjectNode fundingData = dataObject(fundingResponse);
                    if (fundingData != null) {
                        ObjectNode originFunding = objectMapper.createObjectNode();
                        putIfNull(originFunding, "campaignId", positiveLong(fundingData.path("id"), null));
                        putIfBlank(originFunding, "title", textOrNull(fundingData.path("title")));
                        dataNode.set("originFunding", originFunding);
                        putIfBlank(dataNode, "summary", textOrNull(fundingData.path("summary")));
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] origin funding enrichment skipped. itemId={}", itemId, e);
                    return Mono.just(response);
                });
    }

    private void enrichRewardOptions(ObjectNode dataNode, CatalogSalesChannel salesChannel) {
        if (salesChannel != CatalogSalesChannel.FUNDING) {
            return;
        }
        if (dataNode.has("rewardOptions") && dataNode.path("rewardOptions").isArray()
                && dataNode.path("rewardOptions").size() > 0) {
            return;
        }

        ArrayNode rewardOptions = objectMapper.createArrayNode();
        JsonNode itemNode = dataNode.path("item");
        JsonNode options = itemNode.path("options");
        Long basePrice = positiveLong(itemNode.path("price"), positiveLong(dataNode.path("price"), null));
        String shippingText = textOrNull(itemNode.path("shippingInfo").path("shippingNotice"));

        if (options.isArray()) {
            for (JsonNode option : options) {
                Long itemOptionId = positiveLong(option.path("id"), null);
                String title = textOrNull(option.path("optionName"));
                Long additionalPrice = positiveLong(option.path("additionalPrice"), 0L);
                if (itemOptionId == null && !StringUtils.hasText(title)) {
                    continue;
                }

                ObjectNode rewardOption = rewardOptions.addObject();
                if (itemOptionId != null) {
                    rewardOption.put("id", itemOptionId);
                    rewardOption.put("itemOptionId", itemOptionId);
                }
                if (StringUtils.hasText(title)) {
                    rewardOption.put("title", title);
                }
                if (basePrice != null) {
                    rewardOption.put("price", basePrice + (additionalPrice != null ? additionalPrice : 0L));
                }
                if (StringUtils.hasText(shippingText)) {
                    rewardOption.put("shippingText", shippingText);
                }
            }
        }

        dataNode.set("rewardOptions", rewardOptions);
    }

    private Mono<ResponseEntity<JsonNode>> enrichThumbnailUrl(HttpHeaders headers,
                                                              ResponseEntity<JsonNode> response,
                                                              ObjectNode dataNode) {
        if (StringUtils.hasText(textOrNull(dataNode.path("thumbnailUrl")))) {
            return Mono.just(response);
        }

        Long thumbnailMediaId = resolveThumbnailMediaId(dataNode);
        if (thumbnailMediaId == null) {
            return Mono.just(response);
        }
        dataNode.put("thumbnailMediaId", thumbnailMediaId);

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

    private void ensureBaseIdentifiers(ObjectNode dataNode, CatalogDetailRequest request) {
        putIfNull(dataNode, "itemId", request.itemId());
        putIfBlank(dataNode, "itemType", request.itemType() != null ? request.itemType().name() : null);
        putIfBlank(dataNode, "salesChannel", request.salesChannel().name());
        if (request.salesChannel() == CatalogSalesChannel.FUNDING) {
            putIfNull(dataNode, "campaignId",
                    positiveLong(dataNode.path("id"), request.campaignId()));
        }
        if (request.salesChannel() == CatalogSalesChannel.HOT_DEAL) {
            putIfNull(dataNode, "hotDealId",
                    positiveLong(dataNode.path("id"), request.hotDealId()));
        }
    }

    private void mergeItemSummary(ObjectNode target, ObjectNode summary) {
        putIfNull(target, "itemId", positiveLong(summary.path("itemId"), positiveLong(summary.path("id"), null)));
        putIfBlank(target, "title", textOrNull(summary.path("title")));
        putIfNull(target, "price", positiveLong(summary.path("price"), null));
        putIfBlank(target, "status", textOrNull(summary.path("status")));
        putIfBlank(target, "itemType", textOrNull(summary.path("itemType")));
        putIfNull(target, "storeId", positiveLong(summary.path("storeId"), null));

        JsonNode images = summary.path("images");
        if (images.isObject()) {
            JsonNode thumbnail = images.path("thumbnail");
            Long mediaId = positiveLong(thumbnail.path("mediaId"), null);
            putIfNull(target, "thumbnailMediaId", mediaId);
        }
    }

    private void mergeItemData(ObjectNode target, ObjectNode itemData) {
        putIfNull(target, "itemId", positiveLong(itemData.path("itemId"), positiveLong(itemData.path("id"), null)));
        putIfBlank(target, "title", textOrNull(itemData.path("title")));
        putIfNull(target, "price", positiveLong(itemData.path("price"), null));
        putIfBlank(target, "status", textOrNull(itemData.path("status")));
        putIfBlank(target, "itemType", textOrNull(itemData.path("itemType")));
        putIfNull(target, "categoryId", positiveLong(itemData.path("categoryId"), null));
        putIfNull(target, "storeId", positiveLong(itemData.path("storeId"), null));
        putIfBlank(target, "category", resolveCategoryName(itemData));

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

    private void finalizeDetailPayload(CatalogDetailRequest request, ObjectNode dataNode) {
        putIfBlank(dataNode, "salesChannel", request.salesChannel().name());
        putIfBlank(dataNode, "itemType",
                textOrNull(dataNode.path("item").path("itemType")));
        putIfBlank(dataNode, "category", resolveCategoryName(dataNode));
        putNullIfMissing(dataNode, "thumbnailUrl");

        if (!dataNode.has("reviews")) {
            dataNode.putArray("reviews");
        }

        ensureStorePlaceholder(dataNode);
        ensureStockPlaceholder(dataNode);
        ensureCheckout(dataNode, request);

        if (request.salesChannel() == CatalogSalesChannel.FUNDING && !dataNode.has("rewardOptions")) {
            dataNode.putArray("rewardOptions");
        }
        if (request.salesChannel() == CatalogSalesChannel.NORMAL) {
            putNullIfMissing(dataNode, "originFunding");
        }
    }

    private Mono<ResponseEntity<JsonNode>> enrichReviews(Long itemId,
                                                         HttpHeaders headers,
                                                         ResponseEntity<JsonNode> response,
                                                         ObjectNode dataNode) {
        return downstreamClient.fetchReviews(itemId, headers)
                .map(reviewResponse -> {
                    if (!reviewResponse.getStatusCode().is2xxSuccessful()) {
                        return response;
                    }

                    JsonNode content = reviewResponse.getBody()
                            .path("data")
                            .path("content");
                    if (content.isArray()) {
                        dataNode.set("reviews", content.deepCopy());
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CatalogDetail] review enrichment skipped. itemId={}", itemId, e);
                    return Mono.just(response);
                });
    }

    private void ensureStorePlaceholder(ObjectNode dataNode) {
        if (dataNode.has("store") && dataNode.path("store").isObject()) {
            ObjectNode store = (ObjectNode) dataNode.path("store");
            Long storeId = resolveStoreId(dataNode);
            putIfNull(store, "id", storeId);
            putNullIfMissing(store, "name");
            putNullIfMissing(store, "tagline");
            return;
        }

        Long storeId = resolveStoreId(dataNode);
        if (storeId == null) {
            putNullIfMissing(dataNode, "store");
            return;
        }

        ObjectNode store = objectMapper.createObjectNode();
        store.put("id", storeId);
        store.putNull("name");
        store.putNull("tagline");
        dataNode.set("store", store);
    }

    private void ensureStockPlaceholder(ObjectNode dataNode) {
        if (dataNode.has("stock") && dataNode.path("stock").isObject()) {
            ObjectNode stock = (ObjectNode) dataNode.path("stock");
            putNullIfMissing(stock, "availableQuantity");
            putNullIfMissing(stock, "soldQuantity");
            putNullIfMissing(stock, "soldOut");
            if (!stock.has("optionStocks")) {
                stock.putArray("optionStocks");
            }
            return;
        }

        ObjectNode stock = objectMapper.createObjectNode();
        stock.putNull("availableQuantity");
        stock.putNull("soldQuantity");
        stock.putNull("soldOut");
        stock.putArray("optionStocks");
        dataNode.set("stock", stock);
    }

    private void ensureCheckout(ObjectNode dataNode, CatalogDetailRequest request) {
        ObjectNode checkout = dataNode.has("checkout") && dataNode.path("checkout").isObject()
                ? (ObjectNode) dataNode.path("checkout")
                : objectMapper.createObjectNode();

        putIfBlank(checkout, "entryType", CHECKOUT_ENTRY_TYPE);
        if (request.salesChannel() == CatalogSalesChannel.FUNDING) {
            putIfNull(checkout, "campaignId",
                    positiveLong(dataNode.path("campaignId"), request.campaignId()));
        } else {
            putIfNull(checkout, "itemId", resolveItemId(dataNode, request.itemId()));
        }
        if (request.salesChannel() == CatalogSalesChannel.HOT_DEAL) {
            putIfNull(checkout, "hotDealId",
                    positiveLong(dataNode.path("hotDealId"), request.hotDealId()));
        }
        dataNode.set("checkout", checkout);
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

    private Long resolveItemId(ObjectNode dataNode, Long fallback) {
        Long itemId = positiveLong(dataNode.path("itemId"), fallback);
        if (itemId != null) {
            return itemId;
        }
        JsonNode item = dataNode.path("item");
        itemId = positiveLong(item.path("itemId"), positiveLong(item.path("id"), null));
        if (itemId != null) {
            return itemId;
        }
        return fallback;
    }

    private Long resolveStoreId(ObjectNode dataNode) {
        Long storeId = positiveLong(dataNode.path("storeId"), null);
        if (storeId != null) {
            return storeId;
        }
        JsonNode item = dataNode.path("item");
        return positiveLong(item.path("storeId"), null);
    }

    private Long resolveThumbnailMediaId(ObjectNode dataNode) {
        Long mediaId = positiveLong(dataNode.path("thumbnailMediaId"), null);
        if (mediaId != null) {
            return mediaId;
        }
        mediaId = positiveLong(dataNode.path("thumbnail").path("mediaId"), null);
        if (mediaId != null) {
            return mediaId;
        }
        mediaId = positiveLong(dataNode.path("images").path("thumbnail").path("mediaId"), null);
        if (mediaId != null) {
            return mediaId;
        }
        JsonNode item = dataNode.path("item");
        mediaId = positiveLong(item.path("thumbnail").path("mediaId"), null);
        if (mediaId != null) {
            return mediaId;
        }
        return positiveLong(item.path("images").path("thumbnail").path("mediaId"), null);
    }

    private BffItemType resolveItemType(ObjectNode dataNode, BffItemType fallback) {
        if (fallback != null) {
            return fallback;
        }
        String raw = textOrNull(dataNode.path("itemType"));
        if (!StringUtils.hasText(raw)) {
            raw = textOrNull(dataNode.path("item").path("itemType"));
        }
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return BffItemType.fromNullable(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolveCategoryName(ObjectNode dataNode) {
        String category = textOrNull(dataNode.path("category"));
        if (StringUtils.hasText(category)) {
            return category;
        }
        category = textOrNull(dataNode.path("categoryName"));
        if (StringUtils.hasText(category)) {
            return category;
        }
        JsonNode categoryNode = dataNode.path("category");
        if (categoryNode.isObject()) {
            category = textOrNull(categoryNode.path("name"));
            if (StringUtils.hasText(category)) {
                return category;
            }
        }
        JsonNode item = dataNode.path("item");
        if (item.isObject()) {
            category = textOrNull(item.path("categoryName"));
            if (StringUtils.hasText(category)) {
                return category;
            }
            JsonNode nestedCategory = item.path("category");
            if (nestedCategory.isObject()) {
                return textOrNull(nestedCategory.path("name"));
            }
        }
        return null;
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

    private Integer intOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            String raw = node.asText(null);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Boolean booleanOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            String raw = node.asText(null);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return Boolean.parseBoolean(raw.trim());
        }
        return null;
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

    private void putNullableInt(ObjectNode node, String fieldName, Integer value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }

    private void putNullableBoolean(ObjectNode node, String fieldName, Boolean value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }

    private void putNullIfMissing(ObjectNode node, String fieldName) {
        if (!node.has(fieldName)) {
            node.putNull(fieldName);
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

    private ResponseEntity<JsonNode> normalizeSnowflakeIds(ResponseEntity<JsonNode> response) {
        if (response == null) {
            return null;
        }
        SnowflakeJsonFieldNormalizer.normalizeSuccessData(response.getBody());
        return response;
    }

    private record CatalogDetailRequest(Long itemId,
                                        BffItemType itemType,
                                        CatalogSalesChannel salesChannel,
                                        Long hotDealId,
                                        Long campaignId) {
    }
}
