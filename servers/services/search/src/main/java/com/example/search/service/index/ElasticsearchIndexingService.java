package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.search.document.ItemDocument;
import com.example.search.document.SearchSalesChannel;
import com.example.search.exception.SearchErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexingService implements SearchIndexingService {

    private static final String WRITE_ALIAS = ItemDocument.ITEMS_WRITE_ALIAS;
    private static final String CHANNEL_HOT_DEAL = SearchSalesChannel.HOT_DEAL.name();
    private static final String CHANNEL_FUNDING = SearchSalesChannel.FUNDING.name();
    private static final String CHANNEL_NORMAL = SearchSalesChannel.NORMAL.name();
    private static final int CHANNEL_PRIORITY_HOT_DEAL = SearchSalesChannel.HOT_DEAL.priority();
    private static final int CHANNEL_PRIORITY_FUNDING = SearchSalesChannel.FUNDING.priority();
    private static final int CHANNEL_PRIORITY_NORMAL = SearchSalesChannel.NORMAL.priority();

    private final RestClient restClient;

    @Override
    public void indexItem(Long itemId,
                          String title,
                          String category,
                          String domainType,
                          Long price,
                          String status,
                          Integer stock,
                          Long thumbnailMediaId,
                          Long mediaVersion) {
        String normalizedStatus = normalizeStatus(status);
        SearchSalesChannel initialChannel = SearchSalesChannel.fromStatus(normalizedStatus);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("itemId", itemId);
        putIfNotNull(document, "title", title);
        putIfNotNull(document, "category", category);
        putIfNotNull(document, "domainType", domainType);
        putIfNotNull(document, "price", price);
        putIfNotNull(document, "effectivePrice", price);
        putIfNotNull(document, "status", normalizedStatus);
        document.put("salesChannel", initialChannel.name());
        document.put("channelPriority", initialChannel.priority());
        putIfNotNull(document, "stock", stock);
        putIfNotNull(document, "thumbnailMediaId", thumbnailMediaId);
        putIfNotNull(document, "mediaVersion", mediaVersion);
        document.put("createdAt", Instant.now().toString());

        Request request = new Request("PUT", endpointForDoc(itemId));
        request.setJsonEntity(JsonUtils.toJson(document));
        performRequest(request, itemId, "ITEM_CREATED");
    }

    @Override
    public void updateItem(Long itemId, String title, Long price, Long thumbnailMediaId, Long mediaVersion) {
        if (itemId == null) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemId", itemId);
        params.put("title", title);
        params.put("price", price);
        params.put("thumbnailMediaId", thumbnailMediaId);
        params.put("mediaVersion", mediaVersion);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "ctx._source.itemId = params.itemId; " +
                        "if (params.title != null) { ctx._source.title = params.title; } " +
                        "if (params.price != null) { " +
                        "ctx._source.price = params.price; " +
                        "if (ctx._source.activeHotDealId == null) { ctx._source.effectivePrice = params.price; } " +
                        "} " +
                        "boolean thumbnailChanged = " +
                        "(ctx._source.thumbnailMediaId == null && params.thumbnailMediaId != null) || " +
                        "(ctx._source.thumbnailMediaId != null && !ctx._source.thumbnailMediaId.equals(params.thumbnailMediaId)); " +
                        "if (thumbnailChanged) { " +
                        "ctx._source.thumbnailMediaId = params.thumbnailMediaId; " +
                        "ctx._source.thumbnailUrlSnapshot = null; " +
                        "if (params.mediaVersion != null) { ctx._source.mediaVersion = params.mediaVersion; } " +
                        "}");
        script.put("params", params);

        Map<String, Object> upsert = new LinkedHashMap<>();
        upsert.put("itemId", itemId);
        putIfNotNull(upsert, "title", title);
        putIfNotNull(upsert, "price", price);
        putIfNotNull(upsert, "effectivePrice", price);
        putIfNotNull(upsert, "thumbnailMediaId", thumbnailMediaId);
        putIfNotNull(upsert, "mediaVersion", mediaVersion);
        upsert.put("salesChannel", CHANNEL_NORMAL);
        upsert.put("channelPriority", CHANNEL_PRIORITY_NORMAL);
        upsert.put("createdAt", Instant.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);
        body.put("scripted_upsert", true);
        body.put("upsert", upsert);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequest(request, itemId, "ITEM_UPDATED");
    }

    @Override
    public void updateItemStatus(Long itemId, String status) {
        if (itemId == null || !StringUtils.hasText(status)) {
            return;
        }

        String normalizedStatus = normalizeStatus(status);
        SearchSalesChannel statusChannel = SearchSalesChannel.fromStatus(normalizedStatus);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemId", itemId);
        params.put("status", normalizedStatus);
        params.put("statusChannel", statusChannel.name());
        params.put("statusChannelPriority", statusChannel.priority());
        params.put("hotDealChannel", CHANNEL_HOT_DEAL);
        params.put("hotDealPriority", CHANNEL_PRIORITY_HOT_DEAL);
        params.put("fundingChannel", CHANNEL_FUNDING);
        params.put("fundingPriority", CHANNEL_PRIORITY_FUNDING);
        params.put("normalChannel", CHANNEL_NORMAL);
        params.put("normalPriority", CHANNEL_PRIORITY_NORMAL);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "ctx._source.itemId = params.itemId; " +
                        "ctx._source.status = params.status; " +
                        "boolean hasHotDeal = ctx._source.activeHotDealId != null; " +
                        "boolean hasCampaign = ctx._source.activeCampaignId != null; " +
                        "if (hasHotDeal) { " +
                        "ctx._source.salesChannel = params.hotDealChannel; " +
                        "ctx._source.channelPriority = params.hotDealPriority; " +
                        "} else if (hasCampaign) { " +
                        "ctx._source.salesChannel = params.fundingChannel; " +
                        "ctx._source.channelPriority = params.fundingPriority; " +
                        "} else { " +
                        "ctx._source.salesChannel = params.statusChannel; " +
                        "ctx._source.channelPriority = params.statusChannelPriority; " +
                        "if (params.normalChannel.equals(params.statusChannel) && ctx._source.price != null) { " +
                        "ctx._source.effectivePrice = ctx._source.price; " +
                        "} " +
                        "} " +
                        "if (ctx._source.effectivePrice == null && ctx._source.price != null) { " +
                        "ctx._source.effectivePrice = ctx._source.price; " +
                        "}");
        script.put("params", params);

        Map<String, Object> upsert = new LinkedHashMap<>();
        upsert.put("itemId", itemId);
        upsert.put("status", normalizedStatus);
        upsert.put("salesChannel", statusChannel.name());
        upsert.put("channelPriority", statusChannel.priority());
        upsert.put("createdAt", Instant.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);
        body.put("scripted_upsert", true);
        body.put("upsert", upsert);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequest(request, itemId, "ITEM_STATUS_CHANGED");
    }

    @Override
    public void applyHotDealStarted(Long itemId, Long hotDealId, Long discountedPrice) {
        if (itemId == null) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemId", itemId);
        params.put("hotDealId", hotDealId);
        params.put("discountedPrice", discountedPrice);
        params.put("hotDealChannel", CHANNEL_HOT_DEAL);
        params.put("hotDealPriority", CHANNEL_PRIORITY_HOT_DEAL);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "ctx._source.itemId = params.itemId; " +
                        "ctx._source.salesChannel = params.hotDealChannel; " +
                        "ctx._source.channelPriority = params.hotDealPriority; " +
                        "ctx._source.status = 'HOT_DEAL'; " +
                        "if (params.hotDealId != null) { ctx._source.activeHotDealId = params.hotDealId; } " +
                        "if (params.discountedPrice != null) { " +
                        "ctx._source.effectivePrice = params.discountedPrice; " +
                        "} else if (ctx._source.effectivePrice == null && ctx._source.price != null) { " +
                        "ctx._source.effectivePrice = ctx._source.price; " +
                        "}");
        script.put("params", params);

        Map<String, Object> upsert = new LinkedHashMap<>();
        upsert.put("itemId", itemId);
        upsert.put("salesChannel", CHANNEL_HOT_DEAL);
        upsert.put("channelPriority", CHANNEL_PRIORITY_HOT_DEAL);
        upsert.put("status", "HOT_DEAL");
        putIfNotNull(upsert, "activeHotDealId", hotDealId);
        putIfNotNull(upsert, "effectivePrice", discountedPrice);
        upsert.put("createdAt", Instant.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);
        body.put("scripted_upsert", true);
        body.put("upsert", upsert);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequest(request, itemId, "HOT_DEAL_STARTED");
    }

    @Override
    public void applyHotDealEnded(Long itemId, Long hotDealId) {
        if (itemId == null) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemId", itemId);
        params.put("hotDealId", hotDealId);
        params.put("hotDealChannel", CHANNEL_HOT_DEAL);
        params.put("hotDealPriority", CHANNEL_PRIORITY_HOT_DEAL);
        params.put("fundingChannel", CHANNEL_FUNDING);
        params.put("fundingPriority", CHANNEL_PRIORITY_FUNDING);
        params.put("normalChannel", CHANNEL_NORMAL);
        params.put("normalPriority", CHANNEL_PRIORITY_NORMAL);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "ctx._source.itemId = params.itemId; " +
                        "if (params.hotDealId == null || ctx._source.activeHotDealId == null || " +
                        "ctx._source.activeHotDealId.equals(params.hotDealId)) { " +
                        "ctx._source.activeHotDealId = null; " +
                        "} " +
                        "boolean hasHotDeal = ctx._source.activeHotDealId != null; " +
                        "boolean hasCampaign = ctx._source.activeCampaignId != null; " +
                        "if (hasHotDeal) { " +
                        "ctx._source.salesChannel = params.hotDealChannel; " +
                        "ctx._source.channelPriority = params.hotDealPriority; " +
                        "} else if (hasCampaign) { " +
                        "ctx._source.salesChannel = params.fundingChannel; " +
                        "ctx._source.channelPriority = params.fundingPriority; " +
                        "if (ctx._source.status == null || 'HOT_DEAL'.equals(ctx._source.status) || 'ON_SALE'.equals(ctx._source.status)) { " +
                        "ctx._source.status = 'FUNDING'; " +
                        "} " +
                        "} else { " +
                        "ctx._source.salesChannel = params.normalChannel; " +
                        "ctx._source.channelPriority = params.normalPriority; " +
                        "if (ctx._source.price != null) { ctx._source.effectivePrice = ctx._source.price; } " +
                        "if (ctx._source.status == null || 'HOT_DEAL'.equals(ctx._source.status)) { " +
                        "ctx._source.status = 'ON_SALE'; " +
                        "} " +
                        "} " +
                        "if (ctx._source.effectivePrice == null && ctx._source.price != null) { " +
                        "ctx._source.effectivePrice = ctx._source.price; " +
                        "}");
        script.put("params", params);

        Map<String, Object> upsert = new LinkedHashMap<>();
        upsert.put("itemId", itemId);
        upsert.put("salesChannel", CHANNEL_NORMAL);
        upsert.put("channelPriority", CHANNEL_PRIORITY_NORMAL);
        upsert.put("status", "ON_SALE");
        upsert.put("createdAt", Instant.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);
        body.put("scripted_upsert", true);
        body.put("upsert", upsert);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequest(request, itemId, "HOT_DEAL_ENDED");
    }

    @Override
    public void applyFundingCreated(Long itemId, Long campaignId) {
        if (itemId == null) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemId", itemId);
        params.put("campaignId", campaignId);
        params.put("hotDealChannel", CHANNEL_HOT_DEAL);
        params.put("hotDealPriority", CHANNEL_PRIORITY_HOT_DEAL);
        params.put("fundingChannel", CHANNEL_FUNDING);
        params.put("fundingPriority", CHANNEL_PRIORITY_FUNDING);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "ctx._source.itemId = params.itemId; " +
                        "if (params.campaignId != null) { ctx._source.activeCampaignId = params.campaignId; } " +
                        "boolean hasHotDeal = ctx._source.activeHotDealId != null; " +
                        "if (hasHotDeal) { " +
                        "ctx._source.salesChannel = params.hotDealChannel; " +
                        "ctx._source.channelPriority = params.hotDealPriority; " +
                        "} else { " +
                        "ctx._source.salesChannel = params.fundingChannel; " +
                        "ctx._source.channelPriority = params.fundingPriority; " +
                        "if (ctx._source.status == null || 'ON_SALE'.equals(ctx._source.status) || 'DRAFT'.equals(ctx._source.status)) { " +
                        "ctx._source.status = 'FUNDING'; " +
                        "} " +
                        "} " +
                        "if (ctx._source.effectivePrice == null && ctx._source.price != null) { " +
                        "ctx._source.effectivePrice = ctx._source.price; " +
                        "}");
        script.put("params", params);

        Map<String, Object> upsert = new LinkedHashMap<>();
        upsert.put("itemId", itemId);
        upsert.put("salesChannel", CHANNEL_FUNDING);
        upsert.put("channelPriority", CHANNEL_PRIORITY_FUNDING);
        upsert.put("status", "FUNDING");
        putIfNotNull(upsert, "activeCampaignId", campaignId);
        upsert.put("createdAt", Instant.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);
        body.put("scripted_upsert", true);
        body.put("upsert", upsert);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequest(request, itemId, "FUNDING_CREATED");
    }

    @Override
    public void applyFundingClosed(Long itemId, Long campaignId, String terminalStatus) {
        if (itemId == null) {
            return;
        }

        String normalizedStatus = normalizeStatus(terminalStatus);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemId", itemId);
        params.put("campaignId", campaignId);
        params.put("terminalStatus", normalizedStatus);
        params.put("hotDealChannel", CHANNEL_HOT_DEAL);
        params.put("hotDealPriority", CHANNEL_PRIORITY_HOT_DEAL);
        params.put("fundingChannel", CHANNEL_FUNDING);
        params.put("fundingPriority", CHANNEL_PRIORITY_FUNDING);
        params.put("normalChannel", CHANNEL_NORMAL);
        params.put("normalPriority", CHANNEL_PRIORITY_NORMAL);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "ctx._source.itemId = params.itemId; " +
                        "boolean campaignClosed = false; " +
                        "if (params.campaignId == null || ctx._source.activeCampaignId == null || " +
                        "ctx._source.activeCampaignId.equals(params.campaignId)) { " +
                        "ctx._source.activeCampaignId = null; " +
                        "campaignClosed = true; " +
                        "} " +
                        "boolean hasHotDeal = ctx._source.activeHotDealId != null; " +
                        "boolean hasCampaign = ctx._source.activeCampaignId != null; " +
                        "if (hasHotDeal) { " +
                        "ctx._source.salesChannel = params.hotDealChannel; " +
                        "ctx._source.channelPriority = params.hotDealPriority; " +
                        "} else if (hasCampaign) { " +
                        "ctx._source.salesChannel = params.fundingChannel; " +
                        "ctx._source.channelPriority = params.fundingPriority; " +
                        "} else { " +
                        "ctx._source.salesChannel = params.normalChannel; " +
                        "ctx._source.channelPriority = params.normalPriority; " +
                        "if (ctx._source.price != null) { ctx._source.effectivePrice = ctx._source.price; } " +
                        "if (campaignClosed && params.terminalStatus != null) { " +
                        "ctx._source.status = params.terminalStatus; " +
                        "} else if (ctx._source.status == null || 'FUNDING'.equals(ctx._source.status)) { " +
                        "ctx._source.status = 'ON_SALE'; " +
                        "} " +
                        "} " +
                        "if (ctx._source.effectivePrice == null && ctx._source.price != null) { " +
                        "ctx._source.effectivePrice = ctx._source.price; " +
                        "}");
        script.put("params", params);

        Map<String, Object> upsert = new LinkedHashMap<>();
        upsert.put("itemId", itemId);
        upsert.put("salesChannel", CHANNEL_NORMAL);
        upsert.put("channelPriority", CHANNEL_PRIORITY_NORMAL);
        putIfNotNull(upsert, "status", normalizedStatus);
        upsert.put("createdAt", Instant.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);
        body.put("scripted_upsert", true);
        body.put("upsert", upsert);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequest(request, itemId, "FUNDING_CLOSED");
    }

    @Override
    public void updateItemStock(Long itemId, Integer stock) {
        Map<String, Object> partial = new LinkedHashMap<>();
        putIfNotNull(partial, "stock", stock);

        updatePartial(itemId, partial, "STOCK_DECREASED");
    }

    @Override
    public void updateItemStockVersioned(Long itemId, Integer stock, Long stockVersion) {
        if (itemId == null || stock == null || stockVersion == null) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemId", itemId);
        params.put("stock", stock);
        params.put("stockVersion", stockVersion);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "if (ctx._source.stockVersion == null || params.stockVersion > ctx._source.stockVersion) { " +
                        "ctx._source.itemId = params.itemId; " +
                        "ctx._source.stock = params.stock; " +
                        "ctx._source.stockVersion = params.stockVersion; " +
                        "} else { ctx.op = 'none'; }");
        script.put("params", params);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequestAllowMissing(request, itemId, "ITEM_AVAILABLE_STOCK_CHANGED");
    }

    @Override
    public void updateThumbnailSnapshot(Long itemId, Long thumbnailMediaId, String thumbnailUrlSnapshot, Long mediaVersion) {
        if (itemId == null || thumbnailMediaId == null || mediaVersion == null || !StringUtils.hasText(thumbnailUrlSnapshot)) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("thumbnailMediaId", thumbnailMediaId);
        params.put("thumbnailUrlSnapshot", thumbnailUrlSnapshot);
        params.put("mediaVersion", mediaVersion);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("lang", "painless");
        script.put("source",
                "boolean sameMedia = ctx._source.thumbnailMediaId != null && " +
                        "ctx._source.thumbnailMediaId.equals(params.thumbnailMediaId); " +
                        "boolean sameVersion = ctx._source.mediaVersion != null && " +
                        "ctx._source.mediaVersion.equals(params.mediaVersion); " +
                        "if (sameMedia && sameVersion) { " +
                        "ctx._source.thumbnailUrlSnapshot = params.thumbnailUrlSnapshot; " +
                        "} else { ctx.op = 'none'; }");
        script.put("params", params);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("script", script);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequestAllowMissing(request, itemId, "THUMBNAIL_URL_ENRICHED");
    }

    @Override
    public void deleteItem(Long itemId) {
        if (itemId == null) {
            return;
        }
        Request request = new Request("DELETE", endpointForDoc(itemId));
        performRequest(request, itemId, "ITEM_DELETED");
    }

    private void updatePartial(Long itemId, Map<String, Object> partial, String eventType) {
        if (itemId == null || partial.isEmpty()) {
            return;
        }

        partial.put("itemId", itemId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("doc", partial);
        body.put("doc_as_upsert", true);

        Request request = new Request("POST", endpointForUpdate(itemId));
        request.setJsonEntity(JsonUtils.toJson(body));
        performRequest(request, itemId, eventType);
    }

    private void performRequest(Request request, Long itemId, String eventType) {
        try {
            restClient.performRequest(request);
            log.debug("Search index updated. eventType={}, itemId={}", eventType, itemId);
        } catch (IOException e) {
            log.error("Search index request failed. eventType={}, itemId={}", eventType, itemId, e);
            throw new BusinessException(
                    SearchErrorCode.SEARCH_INDEXING_FAILED,
                    "검색 인덱싱에 실패했습니다. itemId=" + itemId + ", eventType=" + eventType,
                    e
            );
        }
    }

    private void performRequestAllowMissing(Request request, Long itemId, String eventType) {
        try {
            restClient.performRequest(request);
            log.debug("Search index updated. eventType={}, itemId={}", eventType, itemId);
        } catch (ResponseException e) {
            int status = e.getResponse() == null ? -1 : e.getResponse().getStatusLine().getStatusCode();
            if (status == 404) {
                log.debug("Search document not found. skip stock sync. eventType={}, itemId={}", eventType, itemId);
                return;
            }
            throw new BusinessException(
                    SearchErrorCode.SEARCH_INDEXING_FAILED,
                    "검색 인덱싱에 실패했습니다. itemId=" + itemId + ", eventType=" + eventType,
                    e
            );
        } catch (IOException e) {
            throw new BusinessException(
                    SearchErrorCode.SEARCH_INDEXING_FAILED,
                    "검색 인덱싱에 실패했습니다. itemId=" + itemId + ", eventType=" + eventType,
                    e
            );
        }
    }

    private String endpointForDoc(Long itemId) {
        return "/" + WRITE_ALIAS + "/_doc/" + itemId;
    }

    private String endpointForUpdate(Long itemId) {
        return "/" + WRITE_ALIAS + "/_update/" + itemId;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
