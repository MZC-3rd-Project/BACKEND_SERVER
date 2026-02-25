package com.example.search.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.util.JsonUtils;
import com.example.search.document.ItemDocument;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.dto.search.response.SearchItemResponse;
import com.example.search.exception.SearchErrorCode;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.query.autocomplete.AutocompleteService;
import com.example.search.service.query.cache.SearchResultCacheService;
import com.example.search.service.query.popular.PopularSearchService;
import com.example.search.util.SearchLogMasker;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryService {

    private static final String CHANNEL_ALL = "ALL";
    private static final Set<String> ALLOWED_CHANNELS = Set.of("ALL", "HOT_DEAL", "FUNDING", "NORMAL");

    private static final Set<String> BLOCKED_EXPOSURE_STATUSES = Set.of(
            "DELETED", "HIDDEN", "PRIVATE", "SOLD_OUT", "ENDED", "INACTIVE"
    );

    private final RestClient restClient;
    private final SearchCursorCodec cursorCodec;
    private final AutocompleteService autocompleteService;
    private final PopularSearchService popularSearchService;
    private final SearchResultCacheService searchResultCacheService;
    private final SearchMetricsService searchMetricsService;

    public CursorResponse<SearchItemResponse> search(SearchRequest request) {
        long startNanos = System.nanoTime();
        validateSearchRequest(request);

        SearchSortType sortType = SearchSortType.from(request.getSort());
        int size = request.getSize() == null ? 20 : request.getSize();

        String cachedJson = searchResultCacheService.get(request).orElse(null);
        if (StringUtils.hasText(cachedJson)) {
            try {
                CursorResponse<SearchItemResponse> cachedResult = parseSearchResponse(cachedJson, size);
                recordKeyword(request.getQ());
                searchMetricsService.recordSearchLatency(Duration.ofNanos(System.nanoTime() - startNanos), true, false);
                return cachedResult;
            } catch (RuntimeException e) {
                log.warn("Cached search result parsing failed. fallbackToLive=true", e);
            }
        }

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

        Request searchRequest = new Request("POST", "/" + ItemDocument.ITEMS_READ_ALIAS + "/_search");
        searchRequest.setJsonEntity(JsonUtils.toJson(body));

        try {
            Response response = restClient.performRequest(searchRequest);
            String json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            CursorResponse<SearchItemResponse> result = parseSearchResponse(json, size);
            searchResultCacheService.put(request, json);
            recordKeyword(request.getQ());
            searchMetricsService.recordSearchLatency(Duration.ofNanos(System.nanoTime() - startNanos), true, false);
            return result;
        } catch (IOException e) {
            boolean timeout = isTimeoutException(e);
            searchMetricsService.recordSearchLatency(Duration.ofNanos(System.nanoTime() - startNanos), false, timeout);
            log.error("Search query failed. qHash={}, sort={}, size={}, timeout={}",
                    SearchLogMasker.keywordHash(request.getQ()),
                    request.getSort(),
                    size,
                    timeout,
                    e);
            throw new BusinessException(SearchErrorCode.SEARCH_TEMPORARILY_UNAVAILABLE,
                    "검색 요청 처리에 실패했습니다.", e);
        }
    }

    private void recordKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        autocompleteService.recordKeyword(keyword);
        popularSearchService.recordKeyword(keyword);
    }

    private Map<String, Object> buildQuery(SearchRequest request) {
        List<Object> must = new ArrayList<>();
        List<Object> filter = new ArrayList<>();
        List<Object> mustNot = new ArrayList<>();

        if (StringUtils.hasText(request.getQ())) {
            Map<String, Object> multiMatch = new LinkedHashMap<>();
            multiMatch.put("query", request.getQ());
            multiMatch.put("fields", List.of("title^3", "description"));
            multiMatch.put("type", "best_fields");
            if (shouldApplyFuzziness(request.getQ())) {
                multiMatch.put("fuzziness", "AUTO:3,6");
                multiMatch.put("prefix_length", 1);
                multiMatch.put("max_expansions", 20);
            }
            must.add(Map.of("multi_match", multiMatch));
        }

        if (StringUtils.hasText(request.getCategory())) {
            filter.add(Map.of("term", Map.of("category", request.getCategory())));
        }

        if (StringUtils.hasText(request.getDomainType())) {
            filter.add(Map.of("term", Map.of("domainType", request.getDomainType().trim().toUpperCase(Locale.ROOT))));
        }

        List<String> statuses = normalizeStatuses(request.getStatus());
        if (!statuses.isEmpty()) {
            filter.add(Map.of("terms", Map.of("status", statuses)));
        }

        if (StringUtils.hasText(request.getChannel()) && !CHANNEL_ALL.equals(request.getChannel())) {
            filter.add(channelFilterClause(request.getChannel()));
        }

        mustNot.add(Map.of("terms", Map.of("status", BLOCKED_EXPOSURE_STATUSES)));

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
        if (!must.isEmpty()) {
            bool.put("must", must);
        } else {
            bool.put("must", List.of(Map.of("match_all", Map.of())));
        }
        if (!filter.isEmpty()) {
            bool.put("filter", filter);
        }
        if (!mustNot.isEmpty()) {
            bool.put("must_not", mustNot);
        }

        return Map.of("bool", bool);
    }

    private List<Map<String, Object>> buildSort(SearchSortType sortType) {
        return switch (sortType) {
            case RELEVANCE -> List.of(
                    Map.of("_score", Map.of("order", "desc")),
                    Map.of("channelPriority", Map.of("order", "desc", "missing", "_last")),
                    Map.of("createdAt", Map.of("order", "desc")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
            case LATEST -> List.of(
                    Map.of("channelPriority", Map.of("order", "desc", "missing", "_last")),
                    Map.of("createdAt", Map.of("order", "desc")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
            case POPULAR -> List.of(
                    Map.of("stock", Map.of("order", "desc", "missing", "_last")),
                    Map.of("channelPriority", Map.of("order", "desc", "missing", "_last")),
                    Map.of("createdAt", Map.of("order", "desc")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
            case PRICE_ASC -> List.of(
                    Map.of("price", Map.of("order", "asc", "missing", "_last")),
                    Map.of("channelPriority", Map.of("order", "desc", "missing", "_last")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
            case PRICE_DESC -> List.of(
                    Map.of("price", Map.of("order", "desc", "missing", "_last")),
                    Map.of("channelPriority", Map.of("order", "desc", "missing", "_last")),
                    Map.of("itemId", Map.of("order", "desc"))
            );
        };
    }

    private Map<String, Object> channelFilterClause(String channel) {
        return switch (channel) {
            case "HOT_DEAL" -> Map.of(
                    "bool", Map.of(
                            "should", List.of(
                                    Map.of("term", Map.of("salesChannel", "HOT_DEAL")),
                                    Map.of("term", Map.of("status", "HOT_DEAL"))
                            ),
                            "minimum_should_match", 1
                    )
            );
            case "FUNDING" -> Map.of(
                    "bool", Map.of(
                            "should", List.of(
                                    Map.of("term", Map.of("salesChannel", "FUNDING")),
                                    Map.of("terms", Map.of("status", List.of("FUNDING", "FUNDED", "FUND_FAILED")))
                            ),
                            "minimum_should_match", 1
                    )
            );
            case "NORMAL" -> Map.of(
                    "bool", Map.of(
                            "should", List.of(
                                    Map.of("term", Map.of("salesChannel", "NORMAL")),
                                    Map.of("term", Map.of("status", "ON_SALE"))
                            ),
                            "minimum_should_match", 1
                    )
            );
            default -> Map.of();
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
        Long price = asLong(source.get("price"));
        String status = asString(source.get("status"));
        String salesChannel = resolveSalesChannel(source, status);
        Integer channelPriority = resolveChannelPriority(source, salesChannel);
        Long effectivePrice = firstNonNull(asLong(source.get("effectivePrice")), price);

        return SearchItemResponse.builder()
                .itemId(asLong(source.get("itemId")))
                .title(asString(source.get("title")))
                .category(asString(source.get("category")))
                .domainType(asString(source.get("domainType")))
                .price(price)
                .effectivePrice(effectivePrice)
                .status(status)
                .salesChannel(salesChannel)
                .channelPriority(channelPriority)
                .activeHotDealId(asLong(source.get("activeHotDealId")))
                .activeCampaignId(asLong(source.get("activeCampaignId")))
                .stock(asInteger(source.get("stock")))
                .thumbnailMediaId(asLong(source.get("thumbnailMediaId")))
                .thumbnailUrl(asString(source.get("thumbnailUrlSnapshot")))
                .score(asDouble(hit.get("_score")))
                .highlightedTitle(firstHighlight(highlight, "title"))
                .highlightedDescription(firstHighlight(highlight, "description"))
                .build();
    }

    private String resolveSalesChannel(Map<String, Object> source, String status) {
        String fromDocument = asString(source.get("salesChannel"));
        if (StringUtils.hasText(fromDocument)) {
            return fromDocument;
        }
        return switch (normalizeStatus(status)) {
            case "HOT_DEAL" -> "HOT_DEAL";
            case "FUNDING", "FUNDED", "FUND_FAILED" -> "FUNDING";
            default -> "NORMAL";
        };
    }

    private Integer resolveChannelPriority(Map<String, Object> source, String salesChannel) {
        Integer fromDocument = asInteger(source.get("channelPriority"));
        if (fromDocument != null) {
            return fromDocument;
        }
        return switch (normalizeStatus(salesChannel)) {
            case "HOT_DEAL" -> 3;
            case "FUNDING" -> 2;
            default -> 1;
        };
    }

    private String normalizeStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private List<String> normalizeStatuses(List<String> statuses) {
        if (CollectionUtils.isEmpty(statuses)) {
            return List.of();
        }

        return statuses.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !BLOCKED_EXPOSURE_STATUSES.contains(value))
                .toList();
    }

    private boolean shouldApplyFuzziness(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        int length = query.trim().length();
        return length >= 3;
    }

    private void validateRange(Long minPrice, Long maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER,
                    "minPrice는 maxPrice보다 클 수 없습니다.");
        }
    }

    private void validateSearchRequest(SearchRequest request) {
        if (request == null) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER, "검색 요청이 비어 있습니다.");
        }
        if (StringUtils.hasText(request.getQ())) {
            request.setQ(request.getQ().trim());
        } else {
            request.setQ(null);
        }

        if (StringUtils.hasText(request.getCategory())) {
            request.setCategory(request.getCategory().trim());
        }
        if (StringUtils.hasText(request.getDomainType())) {
            request.setDomainType(request.getDomainType().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(request.getSort())) {
            request.setSort(request.getSort().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(request.getChannel())) {
            request.setChannel(request.getChannel().trim().toUpperCase(Locale.ROOT));
        } else {
            request.setChannel(CHANNEL_ALL);
        }
        if (!ALLOWED_CHANNELS.contains(request.getChannel())) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER,
                    "channel은 ALL, HOT_DEAL, FUNDING, NORMAL 중 하나여야 합니다.");
        }

        if (request.getMinPrice() != null && request.getMinPrice() < 0) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER, "최소 가격은 0 이상이어야 합니다.");
        }
        if (request.getMaxPrice() != null && request.getMaxPrice() < 0) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER, "최대 가격은 0 이상이어야 합니다.");
        }

        int size = request.getSize() == null ? 20 : request.getSize();
        if (size < 1 || size > 100) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER, "size는 1 이상 100 이하여야 합니다.");
        }
        request.setSize(size);

        validateRange(request.getMinPrice(), request.getMaxPrice());
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

    private boolean isTimeoutException(IOException e) {
        if (e == null) {
            return false;
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause instanceof java.net.SocketTimeoutException;
    }
}
