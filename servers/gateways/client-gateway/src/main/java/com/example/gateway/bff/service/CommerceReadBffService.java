package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.bff.dto.BffItemType;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParseException;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.security.gateway.GatewayContextHeaderCodec;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class CommerceReadBffService {

    private static final String CODE_INVALID_REQUEST = "BFF-READ-400";
    private static final String CODE_DOWNSTREAM_ERROR = "BFF-READ-502";

    private final WebClient fundingWebClient;
    private final WebClient hotDealWebClient;
    private final WebClient salesWebClient;
    private final WebClient productWebClient;
    private final WebClient mediaWebClient;
    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;
    private final ObjectMapper objectMapper;

    public CommerceReadBffService(
            WebClient.Builder webClientBuilder,
            GatewaySessionPrincipalResolver sessionPrincipalResolver,
            GatewaySecurityProperties securityProperties,
            ObjectProvider<HmacSigner> hmacSignerProvider,
            ObjectMapper objectMapper,
            @Value("${app.service.funding-url:http://localhost:8086}") String fundingServiceUrl,
            @Value("${app.service.hot-deal-url:http://localhost:8089}") String hotDealServiceUrl,
            @Value("${app.service.sales-url:http://localhost:8087}") String salesServiceUrl,
            @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl
    ) {
        this.fundingWebClient = webClientBuilder.baseUrl(fundingServiceUrl).build();
        this.hotDealWebClient = webClientBuilder.baseUrl(hotDealServiceUrl).build();
        this.salesWebClient = webClientBuilder.baseUrl(salesServiceUrl).build();
        this.productWebClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.mediaWebClient = webClientBuilder.baseUrl(mediaServiceUrl).build();
        this.sessionPrincipalResolver = sessionPrincipalResolver;
        this.securityProperties = securityProperties;
        this.hmacSignerProvider = hmacSignerProvider;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<JsonNode>> findFundingCampaigns(ServerHttpRequest request) {
        HttpHeaders headers = buildDownstreamHeaders();
        MultiValueMap<String, String> queryParams = copyAllowedQueryParams(
                request,
                List.of("cursor", "size", "status")
        );

        return callGet(fundingWebClient, "/api/campaigns", queryParams, headers)
                .flatMap(response -> enrichFundingCampaignList(response, headers))
                .onErrorResume(e -> {
                    log.warn("[CommerceReadBff] funding list failed", e);
                    return Mono.just(badGateway("펀딩 목록 조회에 실패했습니다"));
                })
                .map(this::normalizeSnowflakeIds);
    }

    public Mono<ResponseEntity<JsonNode>> findClosingSoonFundingCampaigns(ServerHttpRequest request) {
        HttpHeaders headers = buildDownstreamHeaders();
        MultiValueMap<String, String> queryParams = copyAllowedQueryParams(
                request,
                List.of("size")
        );

        return callGet(fundingWebClient, "/api/campaigns/closing-soon", queryParams, headers)
                .flatMap(response -> enrichFundingCampaignList(response, headers))
                .onErrorResume(e -> {
                    log.warn("[CommerceReadBff] funding closing soon failed", e);
                    return Mono.just(badGateway("마감 임박 펀딩 조회에 실패했습니다"));
                })
                .map(this::normalizeSnowflakeIds);
    }

    public Mono<ResponseEntity<JsonNode>> findFundingCampaignDetail(Long campaignId) {
        if (campaignId == null || campaignId <= 0) {
            return Mono.just(badRequest("campaignId는 양수여야 합니다"));
        }

        HttpHeaders headers = buildDownstreamHeaders();
        return callGet(fundingWebClient, "/api/campaigns/" + campaignId, headers)
                .flatMap(response -> enrichFundingCampaignDetail(response, headers))
                .onErrorResume(e -> {
                    log.warn("[CommerceReadBff] funding detail failed. campaignId={}", campaignId, e);
                    return Mono.just(badGateway("펀딩 상세 조회에 실패했습니다"));
                })
                .map(this::normalizeSnowflakeIds);
    }

    public Mono<ResponseEntity<JsonNode>> findHotDeals(ServerHttpRequest request) {
        HttpHeaders headers = buildDownstreamHeaders();
        MultiValueMap<String, String> queryParams = copyAllowedQueryParams(
                request,
                List.of("cursor", "size")
        );

        return callGet(hotDealWebClient, "/api/v1/hot-deals", queryParams, headers)
                .flatMap(response -> enrichHotDealList(response, headers))
                .onErrorResume(e -> {
                    log.warn("[CommerceReadBff] hot-deal list failed", e);
                    return Mono.just(badGateway("핫딜 목록 조회에 실패했습니다"));
                })
                .map(this::normalizeSnowflakeIds);
    }

    public Mono<ResponseEntity<JsonNode>> findHotDealDetail(Long hotDealId) {
        if (hotDealId == null || hotDealId <= 0) {
            return Mono.just(badRequest("hotDealId는 양수여야 합니다"));
        }

        HttpHeaders headers = buildDownstreamHeaders();
        return callGet(hotDealWebClient, "/api/v1/hot-deals/" + hotDealId, headers)
                .flatMap(response -> enrichHotDealDetail(response, headers))
                .onErrorResume(e -> {
                    log.warn("[CommerceReadBff] hot-deal detail failed. hotDealId={}", hotDealId, e);
                    return Mono.just(badGateway("핫딜 상세 조회에 실패했습니다"));
                })
                .map(this::normalizeSnowflakeIds);
    }

    public Mono<ResponseEntity<JsonNode>> findSalesProducts(ServerHttpRequest request) {
        return withOptionalUserContextHeaders(headers -> {
            MultiValueMap<String, String> queryParams = copyAllowedQueryParams(
                    request,
                    List.of("cursor", "size")
            );

            return callGet(productWebClient, "/api/products", queryParams, headers)
                    .flatMap(response -> enrichSalesProductList(response, headers))
                    .onErrorResume(e -> {
                        log.warn("[CommerceReadBff] sales product list failed", e);
                        return Mono.just(badGateway("일반 판매 목록 조회에 실패했습니다"));
                    })
                    .map(this::normalizeSnowflakeIds);
        });
    }

    public Mono<ResponseEntity<JsonNode>> findSalesProductDetail(Long saleId) {
        if (saleId == null || saleId <= 0) {
            return Mono.just(badRequest("saleId는 양수여야 합니다"));
        }

        return withOptionalUserContextHeaders(headers ->
                callGet(productWebClient, "/api/products/" + saleId, headers)
                        .flatMap(response -> enrichSalesProductDetail(response, headers))
                        .onErrorResume(e -> {
                            log.warn("[CommerceReadBff] sales product detail failed. saleId={}", saleId, e);
                            return Mono.just(badGateway("일반 판매 상세 조회에 실패했습니다"));
                        })
                        .map(this::normalizeSnowflakeIds));
    }

    private Mono<ResponseEntity<JsonNode>> enrichFundingCampaignList(ResponseEntity<JsonNode> response,
                                                                     HttpHeaders headers) {
        ObjectNode data = dataObject(response);
        if (data == null) {
            return Mono.just(response);
        }

        JsonNode itemsNode = data.path("items");
        if (!itemsNode.isArray()) {
            return Mono.just(response);
        }

        List<Long> itemIds = new ArrayList<>();
        List<Long> campaignIds = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            if (!node.isObject()) {
                continue;
            }
            Long itemId = positiveLong(node.path("itemId"), null);
            Long campaignId = positiveLong(node.path("id"), null);
            if (itemId != null) {
                itemIds.add(itemId);
            }
            if (campaignId != null) {
                campaignIds.add(campaignId);
            }
        }

        return Mono.zip(
                        fetchItemSummaryMap(itemIds, headers),
                        fetchSupporterCountMap(campaignIds, headers)
                )
                .flatMap(tuple -> {
                    Map<Long, ObjectNode> itemSummaryMap = tuple.getT1();
                    Map<Long, Integer> supporterCountMap = tuple.getT2();
                    List<Long> mediaIds = extractThumbnailMediaIds(itemSummaryMap.values());
                    return fetchMediaUrlMap(mediaIds, headers)
                            .map(mediaUrlMap -> {
                                for (JsonNode node : itemsNode) {
                                    if (!node.isObject()) {
                                        continue;
                                    }
                                    ObjectNode campaignNode = (ObjectNode) node;
                                    Long itemId = positiveLong(campaignNode.path("itemId"), null);
                                    Long campaignId = positiveLong(campaignNode.path("id"), null);

                                    ObjectNode summary = itemId != null ? itemSummaryMap.get(itemId) : null;
                                    if (summary != null) {
                                        putIfBlank(campaignNode, "title", textOrNull(summary.path("title")));
                                        Long thumbnailMediaId = positiveLong(summary.path("images").path("thumbnail").path("mediaId"), null);
                                        putIfNull(campaignNode, "thumbnailMediaId", thumbnailMediaId);
                                        if (thumbnailMediaId != null) {
                                            putIfBlank(campaignNode, "thumbnailUrl", mediaUrlMap.get(thumbnailMediaId));
                                        }
                                    }

                                    enrichProgressRate(campaignNode);
                                    putIfBlank(campaignNode, "leftLabel", toLeftLabel(textOrNull(campaignNode.path("endAt"))));
                                    putIfNull(campaignNode, "supporterCount",
                                            campaignId != null ? longOrNull(supporterCountMap.get(campaignId)) : null);
                                    if (!campaignNode.has("isSupportable")) {
                                        campaignNode.put("isSupportable", isFundingSupportable(campaignNode));
                                    }
                                    ensureFundingListPlaceholders(campaignNode);
                                }
                                return response;
                            });
                })
                .onErrorResume(e -> {
                    log.debug("[CommerceReadBff] funding list enrichment skipped", e);
                    return Mono.just(response);
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichFundingCampaignDetail(ResponseEntity<JsonNode> response,
                                                                       HttpHeaders headers) {
        ObjectNode campaign = dataObject(response);
        if (campaign == null) {
            return Mono.just(response);
        }

        Long campaignId = positiveLong(campaign.path("id"), null);
        Long itemId = positiveLong(campaign.path("itemId"), null);

        Mono<Map<Long, Integer>> supporterMono = campaignId == null
                ? Mono.just(Map.of())
                : fetchSupporterCountMap(List.of(campaignId), headers);
        Mono<Map<Long, ObjectNode>> summaryMono = itemId == null
                ? Mono.just(Map.of())
                : fetchItemSummaryMap(List.of(itemId), headers);

        return Mono.zip(summaryMono, supporterMono)
                .flatMap(tuple -> {
                    Map<Long, ObjectNode> summaryMap = tuple.getT1();
                    Map<Long, Integer> supporterMap = tuple.getT2();

                    ObjectNode summary = itemId != null ? summaryMap.get(itemId) : null;
                    if (summary != null) {
                        putIfBlank(campaign, "title", textOrNull(summary.path("title")));
                        Long thumbnailMediaId = positiveLong(summary.path("images").path("thumbnail").path("mediaId"), null);
                        putIfNull(campaign, "thumbnailMediaId", thumbnailMediaId);
                    }

                    enrichProgressRate(campaign);
                    putIfBlank(campaign, "leftLabel", toLeftLabel(textOrNull(campaign.path("endAt"))));
                    if (campaignId != null) {
                        putIfNull(campaign, "supporterCount", longOrNull(supporterMap.get(campaignId)));
                    }
                    if (!campaign.has("isSupportable")) {
                        campaign.put("isSupportable", isFundingSupportable(campaign));
                    }
                    ensureFundingDetailPlaceholders(campaign);

                    return enrichFundingDetailWithNormalItem(campaign, headers)
                            .flatMap(enriched -> enrichThumbnailUrl(enriched, headers, response))
                            .defaultIfEmpty(response);
                })
                .onErrorResume(e -> {
                    log.debug("[CommerceReadBff] funding detail enrichment skipped", e);
                    return Mono.just(response);
                });
    }

    private Mono<ObjectNode> enrichFundingDetailWithNormalItem(ObjectNode campaign,
                                                               HttpHeaders headers) {
        Long itemId = positiveLong(campaign.path("itemId"), null);
        if (itemId == null) {
            return Mono.just(campaign);
        }

        return fetchItemSummaryMap(List.of(itemId), headers)
                .flatMap(summaryMap -> {
                    ObjectNode summary = summaryMap.get(itemId);
                    if (summary == null) {
                        return Mono.just(campaign);
                    }

                    String itemTypeRaw = textOrNull(summary.path("itemType"));
                    BffItemType itemType = parseItemType(itemTypeRaw);
                    if (itemType == null) {
                        return Mono.just(campaign);
                    }

                    return callGet(productWebClient, itemType.collectionPath() + "/" + itemId, headers)
                            .map(detailResponse -> {
                                ObjectNode detailData = dataObject(detailResponse);
                                if (detailData == null) {
                                    return campaign;
                                }
                                putIfBlank(campaign, "description", textOrNull(detailData.path("description")));
                                putIfNull(campaign, "categoryId", positiveLong(detailData.path("categoryId"), null));
                                if (!campaign.has("item")) {
                                    campaign.set("item", detailData.deepCopy());
                                }
                                return campaign;
                            })
                            .onErrorResume(e -> Mono.just(campaign));
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichHotDealList(ResponseEntity<JsonNode> response,
                                                             HttpHeaders headers) {
        JsonNode dataNode = dataNode(response);
        if (!isSuccessResponse(response) || !dataNode.isArray()) {
            return Mono.just(response);
        }

        List<Long> hotDealIds = new ArrayList<>();
        for (JsonNode node : dataNode) {
            Long hotDealId = positiveLong(node.path("id"), null);
            if (hotDealId != null) {
                hotDealIds.add(hotDealId);
            }
        }

        return fetchHotDealDetailMap(hotDealIds, headers)
                .flatMap(detailMap -> {
                    List<Long> itemIds = new ArrayList<>();
                    for (ObjectNode detail : detailMap.values()) {
                        Long itemId = positiveLong(detail.path("itemId"), null);
                        if (itemId != null) {
                            itemIds.add(itemId);
                        }
                    }
                    return fetchItemSummaryMap(itemIds, headers)
                            .flatMap(summaryMap -> {
                                List<Long> mediaIds = extractThumbnailMediaIds(summaryMap.values());
                                return fetchMediaUrlMap(mediaIds, headers)
                                        .map(mediaUrlMap -> {
                                            for (JsonNode node : dataNode) {
                                                if (!node.isObject()) {
                                                    continue;
                                                }
                                                ObjectNode listItem = (ObjectNode) node;
                                                Long hotDealId = positiveLong(listItem.path("id"), null);
                                                ObjectNode detail = hotDealId != null ? detailMap.get(hotDealId) : null;
                                                if (detail != null) {
                                                    mergeHotDealFields(listItem, detail);
                                                    Long itemId = positiveLong(detail.path("itemId"), null);
                                                    ObjectNode summary = itemId != null ? summaryMap.get(itemId) : null;
                                                    if (summary != null) {
                                                        Long thumbnailMediaId = positiveLong(summary.path("images")
                                                                .path("thumbnail").path("mediaId"), null);
                                                        putIfNull(listItem, "thumbnailMediaId", thumbnailMediaId);
                                                        if (thumbnailMediaId != null) {
                                                            putIfBlank(listItem, "thumbnailUrl", mediaUrlMap.get(thumbnailMediaId));
                                                        }
                                                    }
                                                }
                                                putIfBlank(listItem, "leftLabel", toLeftLabel(textOrNull(listItem.path("endAt"))));
                                                ensureHotDealListPlaceholders(listItem);
                                            }
                                            return response;
                                        });
                            });
                })
                .onErrorResume(e -> {
                    log.debug("[CommerceReadBff] hot-deal list enrichment skipped", e);
                    return Mono.just(response);
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichHotDealDetail(ResponseEntity<JsonNode> response,
                                                               HttpHeaders headers) {
        ObjectNode hotDeal = dataObject(response);
        if (hotDeal == null) {
            return Mono.just(response);
        }

        Long itemId = positiveLong(hotDeal.path("itemId"), null);
        if (itemId == null) {
            putIfBlank(hotDeal, "leftLabel", toLeftLabel(textOrNull(hotDeal.path("endAt"))));
            return Mono.just(response);
        }

        putIfBlank(hotDeal, "leftLabel", toLeftLabel(textOrNull(hotDeal.path("endAt"))));
        ensureHotDealDetailPlaceholders(hotDeal);

        return fetchItemSummaryMap(List.of(itemId), headers)
                .flatMap(summaryMap -> {
                    ObjectNode summary = summaryMap.get(itemId);
                    if (summary == null) {
                        return Mono.just(response);
                    }
                    putIfBlank(hotDeal, "title", textOrNull(summary.path("title")));
                    Long thumbnailMediaId = positiveLong(summary.path("images").path("thumbnail").path("mediaId"), null);
                    putIfNull(hotDeal, "thumbnailMediaId", thumbnailMediaId);

                    String itemTypeRaw = textOrNull(summary.path("itemType"));
                    BffItemType itemType = parseItemType(itemTypeRaw);
                    Mono<ResponseEntity<JsonNode>> detailMono = itemType == null
                            ? Mono.just(response)
                            : callGet(productWebClient, itemType.collectionPath() + "/" + itemId, headers)
                            .map(itemDetailResponse -> {
                                ObjectNode itemData = dataObject(itemDetailResponse);
                                if (itemData != null && !hotDeal.has("item")) {
                                    hotDeal.set("item", itemData.deepCopy());
                                    putIfBlank(hotDeal, "description", textOrNull(itemData.path("description")));
                                }
                                return response;
                            })
                            .onErrorResume(e -> Mono.just(response));

                    return detailMono.flatMap(r -> enrichThumbnailUrl(hotDeal, headers, response));
                })
                .onErrorResume(e -> {
                    log.debug("[CommerceReadBff] hot-deal detail enrichment skipped", e);
                    return Mono.just(response);
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichSalesProductList(ResponseEntity<JsonNode> response,
                                                                  HttpHeaders headers) {
        ObjectNode data = dataObject(response);
        if (data == null) {
            return Mono.just(response);
        }
        JsonNode itemsNode = data.path("items");
        if (!itemsNode.isArray()) {
            return Mono.just(response);
        }

        List<Long> mediaIds = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            Long mediaId = resolveThumbnailMediaId(node);
            if (mediaId != null) {
                mediaIds.add(mediaId);
            }
        }

        return fetchMediaUrlMap(mediaIds, headers)
                .map(mediaUrlMap -> {
                    for (JsonNode node : itemsNode) {
                        if (!node.isObject()) {
                            continue;
                        }
                        ObjectNode item = (ObjectNode) node;
                        Long mediaId = resolveThumbnailMediaId(item);
                        putIfNull(item, "thumbnailMediaId", mediaId);
                        if (mediaId != null) {
                            putIfBlank(item, "thumbnailUrl", mediaUrlMap.get(mediaId));
                        }
                        ensureSalesListPlaceholders(item);
                    }
                    return response;
                })
                .onErrorResume(e -> {
                    log.debug("[CommerceReadBff] sales product list enrichment skipped", e);
                    return Mono.just(response);
                });
    }

    private Mono<ResponseEntity<JsonNode>> enrichSalesProductDetail(ResponseEntity<JsonNode> response,
                                                                    HttpHeaders headers) {
        ObjectNode data = dataObject(response);
        if (data == null) {
            return Mono.just(response);
        }

        putIfNull(data, "thumbnailMediaId", resolveThumbnailMediaId(data));
        ensureSalesDetailPlaceholders(data);
        return enrichThumbnailUrl(data, headers, response)
                .onErrorResume(e -> {
                    log.debug("[CommerceReadBff] sales product detail enrichment skipped", e);
                    return Mono.just(response);
                });
    }

    private Mono<Map<Long, ObjectNode>> fetchHotDealDetailMap(List<Long> hotDealIds,
                                                              HttpHeaders headers) {
        if (hotDealIds == null || hotDealIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(hotDealIds)
                .flatMap(hotDealId -> callGet(hotDealWebClient, "/api/v1/hot-deals/" + hotDealId, headers)
                        .map(this::dataObject)
                        .filter(detail -> detail != null)
                        .map(detail -> Map.entry(hotDealId, detail))
                        .onErrorResume(e -> Mono.empty()))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Map.of());
    }

    private Mono<Map<Long, Integer>> fetchSupporterCountMap(List<Long> campaignIds,
                                                            HttpHeaders headers) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(campaignIds)
                .flatMap(campaignId -> callGet(fundingWebClient,
                                "/api/campaigns/" + campaignId + "/participations",
                                headers)
                        .map(response -> Map.entry(campaignId, extractParticipationCount(response)))
                        .onErrorResume(e -> Mono.just(Map.entry(campaignId, 0))))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Map.of());
    }

    private int extractParticipationCount(ResponseEntity<JsonNode> response) {
        if (!isSuccessResponse(response)) {
            return 0;
        }
        JsonNode data = dataNode(response);
        if (data.isArray()) {
            return data.size();
        }
        JsonNode items = data.path("items");
        if (items.isArray()) {
            return items.size();
        }
        return 0;
    }

    private Mono<Map<Long, ObjectNode>> fetchItemSummaryMap(List<Long> itemIds,
                                                            HttpHeaders headers) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        List<Long> distinctIds = itemIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        return callPost(productWebClient, "/internal/v1/items/batch", distinctIds, headers)
                .map(response -> {
                    if (!isSuccessResponse(response)) {
                        return Map.<Long, ObjectNode>of();
                    }
                    JsonNode data = dataNode(response);
                    if (!data.isArray()) {
                        return Map.<Long, ObjectNode>of();
                    }
                    Map<Long, ObjectNode> result = new LinkedHashMap<>();
                    for (JsonNode node : data) {
                        if (!node.isObject()) {
                            continue;
                        }
                        Long itemId = positiveLong(node.path("id"), null);
                        if (itemId == null) {
                            continue;
                        }
                        result.put(itemId, (ObjectNode) node);
                    }
                    return Map.copyOf(result);
                })
                .onErrorReturn(Map.of());
    }

    private Mono<Map<Long, String>> fetchMediaUrlMap(List<Long> mediaIds,
                                                     HttpHeaders headers) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        List<Long> distinctIds = mediaIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return callPost(mediaWebClient, "/internal/v1/media/urls/batch", Map.of("mediaIds", distinctIds), headers)
                .map(response -> {
                    if (!isSuccessResponse(response)) {
                        return Map.<Long, String>of();
                    }
                    JsonNode data = dataNode(response);
                    if (!data.isArray()) {
                        return Map.<Long, String>of();
                    }
                    Map<Long, String> result = new LinkedHashMap<>();
                    for (JsonNode node : data) {
                        Long mediaId = positiveLong(node.path("mediaId"), null);
                        String mediaUrl = textOrNull(node.path("mediaUrl"));
                        if (mediaId == null || !StringUtils.hasText(mediaUrl)) {
                            continue;
                        }
                        result.put(mediaId, mediaUrl);
                    }
                    return Map.copyOf(result);
                })
                .onErrorReturn(Map.of());
    }

    private List<Long> extractThumbnailMediaIds(Iterable<ObjectNode> summaries) {
        List<Long> mediaIds = new ArrayList<>();
        if (summaries == null) {
            return mediaIds;
        }
        for (ObjectNode summary : summaries) {
            if (summary == null) {
                continue;
            }
            Long mediaId = positiveLong(summary.path("images").path("thumbnail").path("mediaId"), null);
            if (mediaId != null) {
                mediaIds.add(mediaId);
            }
        }
        return mediaIds;
    }

    private Long resolveThumbnailMediaId(JsonNode node) {
        Long directMediaId = positiveLong(node.path("thumbnailMediaId"), null);
        if (directMediaId != null) {
            return directMediaId;
        }
        return positiveLong(node.path("images").path("thumbnail").path("mediaId"), null);
    }

    private Mono<ResponseEntity<JsonNode>> enrichThumbnailUrl(ObjectNode target,
                                                              HttpHeaders headers,
                                                              ResponseEntity<JsonNode> response) {
        if (target == null || StringUtils.hasText(textOrNull(target.path("thumbnailUrl")))) {
            return Mono.just(response);
        }
        Long mediaId = positiveLong(target.path("thumbnailMediaId"), null);
        if (mediaId == null) {
            return Mono.just(response);
        }
        return fetchMediaUrlMap(List.of(mediaId), headers)
                .map(mediaUrlMap -> {
                    String mediaUrl = mediaUrlMap.get(mediaId);
                    if (StringUtils.hasText(mediaUrl)) {
                        target.put("thumbnailUrl", mediaUrl);
                    }
                    return response;
                });
    }

    private void mergeHotDealFields(ObjectNode target, ObjectNode detail) {
        putIfNull(target, "itemId", positiveLong(detail.path("itemId"), null));
        putIfNull(target, "originalPrice", positiveLong(detail.path("originalPrice"), null));
        putIfNull(target, "discountedPrice", positiveLong(detail.path("discountedPrice"), null));
        putIfNull(target, "remainingQuantity", positiveLong(detail.path("remainingQuantity"), null));
        putIfBlank(target, "status", textOrNull(detail.path("status")));
        putIfBlank(target, "startAt", textOrNull(detail.path("startAt")));
        putIfBlank(target, "endAt", textOrNull(detail.path("endAt")));
        if (!target.hasNonNull("progressRate")) {
            putIfDouble(target, "progressRate", detail.path("progressRate").asDouble(Double.NaN));
        }
    }

    private void ensureFundingListPlaceholders(ObjectNode node) {
        putNullIfMissing(node, "thumbnailUrl");
        putNullIfMissing(node, "makerName");
        putNullIfMissing(node, "store");
    }

    private void ensureFundingDetailPlaceholders(ObjectNode node) {
        putNullIfMissing(node, "thumbnailUrl");
        putNullIfMissing(node, "makerName");
        putNullIfMissing(node, "store");
        putNullIfMissing(node, "product");
        if (!node.has("rewardOptions")) {
            node.putArray("rewardOptions");
        }
    }

    private void ensureHotDealListPlaceholders(ObjectNode node) {
        putNullIfMissing(node, "category");
        putNullIfMissing(node, "storeName");
        putNullIfMissing(node, "thumbnailUrl");
    }

    private void ensureHotDealDetailPlaceholders(ObjectNode node) {
        putNullIfMissing(node, "category");
        putNullIfMissing(node, "storeName");
        putNullIfMissing(node, "thumbnailUrl");
        putNullIfMissing(node, "description");
        putNullIfMissing(node, "item");
    }

    private void ensureSalesListPlaceholders(ObjectNode node) {
        putNullIfMissing(node, "fundingTitle");
        putNullIfMissing(node, "storeName");
        putNullIfMissing(node, "category");
        putNullIfMissing(node, "thumbnailUrl");
    }

    private void ensureSalesDetailPlaceholders(ObjectNode node) {
        putNullIfMissing(node, "fundingTitle");
        putNullIfMissing(node, "storeName");
        putNullIfMissing(node, "category");
        putNullIfMissing(node, "thumbnailUrl");
        putNullIfMissing(node, "description");
    }

    private boolean isFundingSupportable(ObjectNode campaign) {
        String status = textOrNull(campaign.path("status"));
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return false;
        }
        Instant endAt = toInstant(textOrNull(campaign.path("endAt")));
        return endAt == null || endAt.isAfter(Instant.now());
    }

    private void enrichProgressRate(ObjectNode campaign) {
        if (campaign.hasNonNull("progressRate")) {
            return;
        }
        Long currentAmount = positiveLong(campaign.path("currentAmount"), 0L);
        Long goalAmount = positiveLong(campaign.path("goalAmount"), 0L);
        if (goalAmount == null || goalAmount <= 0) {
            campaign.put("progressRate", 0.0);
            return;
        }
        double rate = (double) currentAmount / goalAmount * 100.0;
        double rounded = Math.round(rate * 100) / 100.0;
        campaign.put("progressRate", rounded);
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

    private BffItemType parseItemType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return BffItemType.fromNullable(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Mono<ResponseEntity<JsonNode>> callGet(WebClient webClient, String path, HttpHeaders headers) {
        return callGet(webClient, path, new LinkedMultiValueMap<>(), headers);
    }

    private Mono<ResponseEntity<JsonNode>> callGet(WebClient webClient,
                                                   String path,
                                                   MultiValueMap<String, String> queryParams,
                                                   HttpHeaders headers) {
        return webClient.method(HttpMethod.GET)
                .uri(uriBuilder -> buildUri(uriBuilder, path, queryParams))
                .headers(h -> h.addAll(headers))
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }

    private Mono<ResponseEntity<JsonNode>> callPost(WebClient webClient,
                                                    String path,
                                                    Object requestBody,
                                                    HttpHeaders headers) {
        return webClient.method(HttpMethod.POST)
                .uri(path)
                .headers(h -> h.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }

    private java.net.URI buildUri(UriBuilder uriBuilder,
                                  String path,
                                  MultiValueMap<String, String> queryParams) {
        UriBuilder builder = uriBuilder.path(path);
        if (queryParams == null || queryParams.isEmpty()) {
            return builder.build();
        }
        queryParams.forEach((name, values) -> {
            if (!StringUtils.hasText(name) || values == null || values.isEmpty()) {
                return;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    builder.queryParam(name, value);
                }
            }
        });
        return builder.build();
    }

    private MultiValueMap<String, String> copyAllowedQueryParams(ServerHttpRequest request,
                                                                 List<String> allowedKeys) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (request == null || allowedKeys == null || allowedKeys.isEmpty()) {
            return params;
        }
        for (String key : allowedKeys) {
            List<String> values = request.getQueryParams().get(key);
            if (values == null || values.isEmpty()) {
                continue;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    params.add(key, value.trim());
                }
            }
        }
        return params;
    }

    private boolean isSuccessResponse(ResponseEntity<JsonNode> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            return false;
        }
        JsonNode body = response.getBody();
        return body != null && body.path("success").asBoolean(false);
    }

    private JsonNode dataNode(ResponseEntity<JsonNode> response) {
        if (!isSuccessResponse(response)) {
            return objectMapper.createObjectNode();
        }
        JsonNode body = response.getBody();
        if (body == null) {
            return objectMapper.createObjectNode();
        }
        return body.path("data");
    }

    private ObjectNode dataObject(ResponseEntity<JsonNode> response) {
        JsonNode data = dataNode(response);
        if (!data.isObject()) {
            return null;
        }
        return (ObjectNode) data;
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
        if (node == null || !StringUtils.hasText(value)) {
            return;
        }
        if (!StringUtils.hasText(textOrNull(node.path(fieldName)))) {
            node.put(fieldName, value);
        }
    }

    private void putIfNull(ObjectNode node, String fieldName, Long value) {
        if (node == null || value == null) {
            return;
        }
        if (!node.hasNonNull(fieldName)) {
            node.put(fieldName, value);
        }
    }

    private void putIfDouble(ObjectNode node, String fieldName, double value) {
        if (node == null || Double.isNaN(value)) {
            return;
        }
        if (!node.hasNonNull(fieldName)) {
            node.put(fieldName, value);
        }
    }

    private void putNullIfMissing(ObjectNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return;
        }
        if (!node.has(fieldName)) {
            node.putNull(fieldName);
        }
    }

    private Long longOrNull(Integer value) {
        if (value == null) {
            return null;
        }
        return value.longValue();
    }

    private HttpHeaders buildDownstreamHeaders() {
        return buildDownstreamHeaders(null);
    }

    private HttpHeaders buildDownstreamHeaders(GatewaySessionPrincipal principal) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
        if (principal == null) {
            return headers;
        }
        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        headers.set(HttpHeaderNames.USER_ROLES, principal.rolesHeaderValue());
        if (StringUtils.hasText(principal.sessionId())) {
            headers.set(HttpHeaderNames.SESSION_ID, principal.sessionId());
        }
        String gatewayContext = createSignedContextHeader(principal);
        if (StringUtils.hasText(gatewayContext)) {
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext);
        }
        return headers;
    }

    private Mono<ResponseEntity<JsonNode>> withOptionalUserContextHeaders(
            Function<HttpHeaders, Mono<ResponseEntity<JsonNode>>> callback) {
        return sessionPrincipalResolver.resolveFromSecurityContext()
                .map(Optional::of)
                .onErrorResume(SessionClaimParseException.class, error -> {
                    log.info("[CommerceReadBff] optional user context skipped. reason={}", error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalPrincipal -> callback.apply(buildDownstreamHeaders(optionalPrincipal.orElse(null))));
    }

    private String createSignedContextHeader(GatewaySessionPrincipal principal) {
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null) {
            return null;
        }

        String userId = String.valueOf(principal.userId());
        String roles = principal.rolesHeaderValue();
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        return GatewayContextHeaderCodec.encodeSigned(userId, roles, nonce, timestamp, signer);
    }

    private ResponseEntity<JsonNode> badRequest(String message) {
        return ResponseEntity.badRequest().body(errorPayload(CODE_INVALID_REQUEST, message));
    }

    private ResponseEntity<JsonNode> badGateway(String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorPayload(CODE_DOWNSTREAM_ERROR, message));
    }

    private ObjectNode errorPayload(String code, String message) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", false);
        ObjectNode error = body.putObject("error");
        error.put("code", code);
        error.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return body;
    }

    private ResponseEntity<JsonNode> normalizeSnowflakeIds(ResponseEntity<JsonNode> response) {
        if (response == null) {
            return null;
        }
        SnowflakeJsonFieldNormalizer.normalizeSuccessData(response.getBody());
        return response;
    }
}
