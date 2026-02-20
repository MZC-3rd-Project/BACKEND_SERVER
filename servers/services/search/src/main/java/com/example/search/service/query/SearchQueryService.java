package com.example.search.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.util.JsonUtils;
import com.example.search.document.ItemDocument;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchItemResponse;
import com.example.search.exception.SearchErrorCode;
import com.example.search.service.query.autocomplete.AutocompleteService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryService {

    private final RestClient restClient;
    private final SearchCursorCodec cursorCodec;
    private final AutocompleteService autocompleteService;

    public CursorResponse<SearchItemResponse> search(SearchRequest request) {
        validateRange(request.getMinPrice(), request.getMaxPrice());

        SearchSortType sortType = SearchSortType.from(request.getSort());
        int size = request.getSize() == null ? 20 : request.getSize();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("track_total_hits", true);
        body.put("size", size);
        body.put("query", buildQuery(request));
        body.put("sort", buildSort(sortType));
        body.put("highlight", buildHighlight());

        List<Object> searchAfter = cursorCodec.decode(request.getCursor());
        if (!searchAfter.isEmpty()) {
            body.put("search_after", searchAfter);
        }

        Request searchRequest = new Request("POST", "/" + ItemDocument.ITEMS_INDEX + "/_search");
        searchRequest.setJsonEntity(JsonUtils.toJson(body));

        try {
            Response response = restClient.performRequest(searchRequest);
            String json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            CursorResponse<SearchItemResponse> result = parseSearchResponse(json, size);
            autocompleteService.recordKeyword(request.getQ());
            return result;
        } catch (IOException e) {
            log.error("Search query failed. request={}", JsonUtils.toJson(body), e);
            throw new BusinessException(SearchErrorCode.SEARCH_TEMPORARILY_UNAVAILABLE,
                    "검색 요청 처리에 실패했습니다.", e);
        }
    }

    private Map<String, Object> buildQuery(SearchRequest request) {
        List<Object> must = new ArrayList<>();
        List<Object> filter = new ArrayList<>();

        must.add(Map.of("multi_match", Map.of(
                "query", request.getQ(),
                "fields", List.of("title^3", "description"),
                "type", "best_fields"
        )));

        if (StringUtils.hasText(request.getCategory())) {
            filter.add(Map.of("term", Map.of("category", request.getCategory())));
        }

        List<String> statuses = normalizeStatuses(request.getStatus());
        if (!statuses.isEmpty()) {
            filter.add(Map.of("terms", Map.of("status", statuses)));
        }

        Map<String, Object> priceRange = new LinkedHashMap<>();
        if (request.getMinPrice() != null) {
            priceRange.put("gte", request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            priceRange.put("lte", request.getMaxPrice());
        }
        if (!priceRange.isEmpty()) {
            filter.add(Map.of("range", Map.of("price", priceRange)));
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", must);
        if (!filter.isEmpty()) {
            bool.put("filter", filter);
        }

        return Map.of("bool", bool);
    }

    private List<Map<String, Object>> buildSort(SearchSortType sortType) {
        return switch (sortType) {
            case LATEST -> List.of(
                    Map.of("createdAt", Map.of("order", "desc")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
            case POPULAR -> List.of(
                    Map.of("stock", Map.of("order", "desc", "missing", "_last")),
                    Map.of("createdAt", Map.of("order", "desc")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
            case PRICE_ASC -> List.of(
                    Map.of("price", Map.of("order", "asc", "missing", "_last")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
            case PRICE_DESC -> List.of(
                    Map.of("price", Map.of("order", "desc", "missing", "_last")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
        };
    }

    private Map<String, Object> buildHighlight() {
        return Map.of("fields", Map.of(
                "title", Map.of(),
                "description", Map.of()
        ));
    }

    private CursorResponse<SearchItemResponse> parseSearchResponse(String json, int size) {
        Map<String, Object> root = JsonUtils.fromJson(json, new TypeReference<>() {
        });
        Map<String, Object> hits = toMap(root.get("hits"));
        List<Map<String, Object>> hitList = toMapList(hits.get("hits"));

        List<SearchItemResponse> items = hitList.stream()
                .map(this::toSearchItem)
                .toList();

        Long total = extractTotal(hits.get("total"));

        String nextCursor = null;
        if (items.size() == size && !hitList.isEmpty()) {
            List<Object> lastSort = toObjectList(hitList.get(hitList.size() - 1).get("sort"));
            nextCursor = cursorCodec.encode(lastSort);
        }

        return CursorResponse.of(items, nextCursor, total);
    }

    private SearchItemResponse toSearchItem(Map<String, Object> hit) {
        Map<String, Object> source = toMap(hit.get("_source"));
        Map<String, Object> highlight = toMap(hit.get("highlight"));

        return SearchItemResponse.builder()
                .itemId(asLong(source.get("itemId")))
                .title(asString(source.get("title")))
                .category(asString(source.get("category")))
                .price(asLong(source.get("price")))
                .status(asString(source.get("status")))
                .stock(asInteger(source.get("stock")))
                .score(asDouble(hit.get("_score")))
                .highlightedTitle(firstHighlight(highlight, "title"))
                .highlightedDescription(firstHighlight(highlight, "description"))
                .build();
    }

    private List<String> normalizeStatuses(List<String> statuses) {
        if (CollectionUtils.isEmpty(statuses)) {
            return List.of();
        }

        return statuses.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .toList();
    }

    private void validateRange(Long minPrice, Long maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER,
                    "minPrice는 maxPrice보다 클 수 없습니다.");
        }
    }

    private String firstHighlight(Map<String, Object> highlight, String key) {
        List<Object> values = toObjectList(highlight.get(key));
        if (values.isEmpty()) {
            return null;
        }
        return asString(values.get(0));
    }

    private Long extractTotal(Object totalRaw) {
        if (totalRaw instanceof Number number) {
            return number.longValue();
        }
        Map<String, Object> totalMap = toMap(totalRaw);
        return asLong(totalMap.get("value"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> toObjectList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
