package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.example.gateway.bff.dto.catalog.CatalogDetailTargetResponse;
import com.example.gateway.bff.dto.catalog.CatalogItemCardResponse;
import com.example.gateway.bff.dto.catalog.CatalogItemsDataResponse;
import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import com.example.gateway.bff.dto.catalog.CatalogPriceResponse;
import com.example.gateway.bff.dto.catalog.CatalogQueryParams;
import com.example.gateway.bff.dto.catalog.CatalogSalesChannel;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class CatalogResponseMapper {

    public CatalogItemsResponse toCatalogResponse(JsonNode searchResponseBody, CatalogQueryParams params) {
        if (searchResponseBody == null || !searchResponseBody.path("success").asBoolean()) {
            throw new IllegalArgumentException("검색 응답 형식이 올바르지 않습니다");
        }

        JsonNode dataNode = searchResponseBody.path("data");
        if (!dataNode.isObject()) {
            throw new IllegalArgumentException("검색 응답 data 형식이 올바르지 않습니다");
        }

        JsonNode itemsNode = dataNode.path("items");
        List<CatalogItemCardResponse> catalogItems = toCatalogItems(itemsNode, params);
        String nextCursor = textOrNull(dataNode.path("nextCursor"));
        Long totalCount = asNullableLong(dataNode.path("totalCount"));

        return CatalogItemsResponse.success(new CatalogItemsDataResponse(catalogItems, nextCursor, totalCount));
    }

    private List<CatalogItemCardResponse> toCatalogItems(JsonNode itemsNode, CatalogQueryParams params) {
        if (!itemsNode.isArray()) {
            return List.of();
        }

        String searchQueryHash = hashQuery(params.query());
        List<CatalogItemCardResponse> results = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            if (!itemNode.isObject()) {
                continue;
            }
            CatalogItemCardResponse mapped = mapItemCard(itemNode, params.itemType(), searchQueryHash);
            if (mapped == null) {
                continue;
            }
            if (params.channel() != CatalogSalesChannel.ALL
                    && mapped.salesChannel() != params.channel()) {
                continue;
            }
            results.add(mapped);
        }
        return List.copyOf(results);
    }

    private CatalogItemCardResponse mapItemCard(JsonNode itemNode, BffItemType requestItemType, String searchQueryHash) {
        Long itemId = asNullableLong(itemNode.path("itemId"));
        if (itemId == null || itemId <= 0) {
            return null;
        }

        BffItemType itemType = resolveItemType(itemNode.path("domainType"), requestItemType);
        String status = resolveStatus(itemNode.path("status"));
        CatalogSalesChannel salesChannel = resolveSalesChannel(itemNode.path("salesChannel"), status);
        Long activeHotDealId = asNullableLong(itemNode.path("activeHotDealId"));
        Long activeCampaignId = asNullableLong(itemNode.path("activeCampaignId"));

        CatalogPriceResponse price = resolvePrice(itemNode);
        CatalogDetailTargetResponse detailTarget = resolveDetailTarget(
                itemId, itemType, salesChannel, activeHotDealId, activeCampaignId, searchQueryHash
        );

        return new CatalogItemCardResponse(
                itemId,
                textOrNull(itemNode.path("title")),
                itemType,
                salesChannel,
                status,
                price,
                asNullableInteger(itemNode.path("stock")),
                asNullableInteger(itemNode.path("availableStock")),
                asNullableLong(itemNode.path("thumbnailMediaId")),
                textOrNull(itemNode.path("thumbnailUrl")),
                activeHotDealId,
                activeCampaignId,
                detailTarget
        );
    }

    private CatalogDetailTargetResponse resolveDetailTarget(Long itemId,
                                                            BffItemType itemType,
                                                            CatalogSalesChannel salesChannel,
                                                            Long activeHotDealId,
                                                            Long activeCampaignId,
                                                            String searchQueryHash) {
        return switch (salesChannel) {
            case HOT_DEAL -> {
                if (activeHotDealId != null && activeHotDealId > 0) {
                    if (itemType != null) {
                        yield new CatalogDetailTargetResponse("HOT_DEAL",
                                buildCatalogDetailRoutePath(itemId, itemType, CatalogSalesChannel.HOT_DEAL, activeHotDealId, null, searchQueryHash));
                    }
                    yield new CatalogDetailTargetResponse("HOT_DEAL", "/api/v1/hot-deals/" + activeHotDealId);
                }
                yield new CatalogDetailTargetResponse("NORMAL", buildNormalDetailPath(itemId, itemType, searchQueryHash));
            }
            case FUNDING -> {
                if (itemType != null) {
                    yield new CatalogDetailTargetResponse("FUNDING",
                            buildCatalogDetailRoutePath(itemId, itemType, CatalogSalesChannel.FUNDING, null, activeCampaignId, searchQueryHash));
                }
                if (activeCampaignId != null && activeCampaignId > 0) {
                    yield new CatalogDetailTargetResponse("FUNDING", "/api/campaigns/" + activeCampaignId);
                }
                yield new CatalogDetailTargetResponse("FUNDING", "/api/campaigns/item/" + itemId);
            }
            case NORMAL, ALL -> new CatalogDetailTargetResponse("NORMAL", buildNormalDetailPath(itemId, itemType, searchQueryHash));
        };
    }

    private String buildCatalogDetailRoutePath(Long itemId,
                                               BffItemType itemType,
                                               CatalogSalesChannel salesChannel,
                                               Long hotDealId,
                                               Long campaignId,
                                               String searchQueryHash) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/bff/v1/catalog/items/{itemId}/detail")
                .queryParam("itemType", itemType.name())
                .queryParam("salesChannel", salesChannel.name());

        if (hotDealId != null && hotDealId > 0) {
            builder.queryParam("hotDealId", hotDealId);
        }
        if (campaignId != null && campaignId > 0) {
            builder.queryParam("campaignId", campaignId);
        }
        if (StringUtils.hasText(searchQueryHash)) {
            builder.queryParam("searchQueryHash", searchQueryHash);
        }
        return builder.buildAndExpand(itemId).toUriString();
    }

    private String buildNormalDetailPath(Long itemId, BffItemType itemType, String searchQueryHash) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/bff/v1/items/{itemId}");
        if (itemType != null) {
            builder.queryParam("type", itemType.name());
        }
        if (StringUtils.hasText(searchQueryHash)) {
            builder.queryParam("searchQueryHash", searchQueryHash);
        }
        return builder.buildAndExpand(itemId).toUriString();
    }

    private String hashQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return null;
        }

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawQuery.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("search query hash generation failed", exception);
        }
    }

    private CatalogPriceResponse resolvePrice(JsonNode itemNode) {
        Long basePrice = firstNonNull(
                asNullableLong(itemNode.path("basePrice")),
                asNullableLong(itemNode.path("price"))
        );
        Long effectivePrice = firstNonNull(
                asNullableLong(itemNode.path("effectivePrice")),
                asNullableLong(itemNode.path("discountPrice")),
                basePrice
        );
        if (basePrice == null && effectivePrice == null) {
            return null;
        }
        return new CatalogPriceResponse(basePrice, effectivePrice);
    }

    private CatalogSalesChannel resolveSalesChannel(JsonNode channelNode, String status) {
        String rawChannel = textOrNull(channelNode);
        if (StringUtils.hasText(rawChannel)) {
            try {
                CatalogSalesChannel parsed = CatalogSalesChannel.fromNullable(rawChannel);
                if (parsed != CatalogSalesChannel.ALL) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                return CatalogSalesChannel.fromStatus(status);
            }
        }
        return CatalogSalesChannel.fromStatus(status);
    }

    private String resolveStatus(JsonNode statusNode) {
        String status = textOrNull(statusNode);
        if (!StringUtils.hasText(status)) {
            return "ON_SALE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private BffItemType resolveItemType(JsonNode domainTypeNode, BffItemType requestItemType) {
        String domainType = textOrNull(domainTypeNode);
        if (!StringUtils.hasText(domainType)) {
            return requestItemType;
        }
        try {
            return BffItemType.fromNullable(domainType);
        } catch (IllegalArgumentException e) {
            return requestItemType;
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Long asNullableLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            String raw = node.asText(null);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asNullableInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            String raw = node.asText(null);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private Long firstNonNull(Long first, Long second, Long third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }
}
