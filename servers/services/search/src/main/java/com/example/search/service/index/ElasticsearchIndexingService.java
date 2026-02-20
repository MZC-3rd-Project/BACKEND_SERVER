package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.search.document.ItemDocument;
import com.example.search.exception.SearchErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexingService implements SearchIndexingService {

    private static final String INDEX_NAME = ItemDocument.ITEMS_INDEX;

    private final RestClient restClient;

    @Override
    public void indexItem(Long itemId,
                          String title,
                          String category,
                          Long price,
                          String status,
                          Integer stock) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("itemId", itemId);
        putIfNotNull(document, "title", title);
        putIfNotNull(document, "category", category);
        putIfNotNull(document, "price", price);
        putIfNotNull(document, "status", status);
        putIfNotNull(document, "stock", stock);
        document.put("createdAt", Instant.now().toString());

        Request request = new Request("PUT", endpointForDoc(itemId));
        request.setJsonEntity(JsonUtils.toJson(document));
        performRequest(request, itemId, "ITEM_CREATED");
    }

    @Override
    public void updateItem(Long itemId, String title, Long price) {
        Map<String, Object> partial = new LinkedHashMap<>();
        putIfNotNull(partial, "title", title);
        putIfNotNull(partial, "price", price);

        updatePartial(itemId, partial, "ITEM_UPDATED");
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

    private String endpointForDoc(Long itemId) {
        return "/" + INDEX_NAME + "/_doc/" + itemId;
    }

    private String endpointForUpdate(Long itemId) {
        return "/" + INDEX_NAME + "/_update/" + itemId;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
