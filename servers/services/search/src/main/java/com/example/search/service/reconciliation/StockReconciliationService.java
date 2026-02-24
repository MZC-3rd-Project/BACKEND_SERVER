package com.example.search.service.reconciliation;

import com.example.core.util.JsonUtils;
import com.example.clients.stock.facade.StockAvailabilityQueryFacade;
import com.example.search.document.ItemDocument;
import com.example.search.dto.reconciliation.response.StockReconciliationResponse;
import com.example.search.service.metrics.SearchMetricsService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReconciliationService {

    private final RestClient restClient;
    private final StockAvailabilityQueryFacade stockQueryClient;
    private final SearchMetricsService searchMetricsService;

    @Value("${search.metrics.runbook-url:https://runbook.example/search}")
    private String runbookUrl;

    public StockReconciliationResponse reconcileItem(Long itemId) {
        Integer sourceStock;
        try {
            sourceStock = stockQueryClient.fetchAvailableStockTotal(itemId);
        } catch (Exception e) {
            searchMetricsService.recordStockReconciliation("source_error");
            return buildResponse(itemId, null, null, false, "source_error");
        }

        Integer indexedStock;
        try {
            indexedStock = fetchIndexedStock(itemId);
        } catch (Exception e) {
            searchMetricsService.recordStockReconciliation("index_error");
            log.warn("[StockReconcile] index read failed. itemId={}, runbook={}", itemId, runbookUrl, e);
            return buildResponse(itemId, sourceStock, null, false, "index_error");
        }

        if (indexedStock == null) {
            searchMetricsService.recordStockReconciliation("index_missing");
            log.warn("[StockReconcile] indexed document missing. itemId={}, sourceStock={}, runbook={}",
                    itemId, sourceStock, runbookUrl);
            return buildResponse(itemId, sourceStock, null, false, "index_missing");
        }

        boolean matched = sourceStock.equals(indexedStock);
        String result = matched ? "matched" : "mismatch";
        searchMetricsService.recordStockReconciliation(result);
        if (!matched) {
            log.warn("[StockReconcile] stock mismatch detected. itemId={}, sourceStock={}, indexedStock={}, runbook={}",
                    itemId, sourceStock, indexedStock, runbookUrl);
        }
        return buildResponse(itemId, sourceStock, indexedStock, matched, result);
    }

    private Integer fetchIndexedStock(Long itemId) throws Exception {
        Request request = new Request("GET", "/" + ItemDocument.ITEMS_READ_ALIAS + "/_doc/" + itemId);
        try {
            Response response = restClient.performRequest(request);
            String json = EntityUtils.toString(response.getEntity());
            Map<String, Object> root = JsonUtils.fromJson(json, new TypeReference<>() {
            });
            Object found = root.get("found");
            if (found instanceof Boolean foundValue && !foundValue) {
                return null;
            }

            Map<String, Object> source = toMap(root.get("_source"));
            if (source.isEmpty()) {
                return null;
            }
            return asInteger(source.get("stock"));
        } catch (ResponseException e) {
            int status = e.getResponse() == null ? -1 : e.getResponse().getStatusLine().getStatusCode();
            if (status == 404) {
                return null;
            }
            throw e;
        }
    }

    private StockReconciliationResponse buildResponse(Long itemId,
                                                      Integer sourceStock,
                                                      Integer indexedStock,
                                                      boolean matched,
                                                      String result) {
        return StockReconciliationResponse.builder()
                .itemId(itemId)
                .sourceAvailableStock(sourceStock)
                .indexedStock(indexedStock)
                .matched(matched)
                .result(result)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
