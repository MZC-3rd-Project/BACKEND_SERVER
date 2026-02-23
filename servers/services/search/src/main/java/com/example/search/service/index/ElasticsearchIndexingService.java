package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.search.document.ItemDocument;
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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexingService implements SearchIndexingService {

    private static final String WRITE_ALIAS = ItemDocument.ITEMS_WRITE_ALIAS;

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
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("itemId", itemId);
        putIfNotNull(document, "title", title);
        putIfNotNull(document, "category", category);
        putIfNotNull(document, "domainType", domainType);
        putIfNotNull(document, "price", price);
        putIfNotNull(document, "status", status);
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
                        "if (params.price != null) { ctx._source.price = params.price; } " +
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
        putIfNotNull(upsert, "thumbnailMediaId", thumbnailMediaId);
        putIfNotNull(upsert, "mediaVersion", mediaVersion);
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
        Map<String, Object> partial = new LinkedHashMap<>();
        putIfNotNull(partial, "status", status);

        updatePartial(itemId, partial, "ITEM_STATUS_CHANGED");
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

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
