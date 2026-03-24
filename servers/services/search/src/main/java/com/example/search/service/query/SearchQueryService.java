package com.example.search.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.search.dto.response.SearchItemResponse;
import com.example.search.service.index.ElasticsearchDocumentClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchQueryService {

    private final ObjectMapper objectMapper;
    private final ElasticsearchDocumentClient elasticsearchDocumentClient;

    public CursorResponse<SearchItemResponse> search(SearchQuery query) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("from", query.offset());
        requestBody.put("size", query.size());
        requestBody.put("track_total_hits", true);
        if (query.sortType().usesScore(query.hasKeyword())) {
            requestBody.put("track_scores", true);
        }
        requestBody.set("query", buildQuery(query));
        requestBody.set("sort", buildSort(query));

        JsonNode response = elasticsearchDocumentClient.search(requestBody);
        JsonNode hitNodes = response.path("hits").path("hits");
        List<SearchItemResponse> items = new ArrayList<>();
        if (hitNodes.isArray()) {
            for (JsonNode hitNode : hitNodes) {
                JsonNode source = hitNode.path("_source");
                if (!source.isObject()) {
                    continue;
                }
                SearchItemResponse item = SearchItemResponse.from(source);
                if (item.itemId() != null) {
                    items.add(item);
                }
            }
        }

        Long totalCount = extractTotalCount(response);
        String nextCursor = hasNextPage(query.offset(), items.size(), totalCount)
                ? SearchCursorCodec.encodeOffset(query.offset() + items.size())
                : null;

        return CursorResponse.of(List.copyOf(items), nextCursor, totalCount);
    }

    private ObjectNode buildQuery(SearchQuery query) {
        ObjectNode boolNode = objectMapper.createObjectNode();
        ArrayNode filterArray = boolNode.putArray("filter");
        appendFilters(query, filterArray);

        if (query.hasKeyword()) {
            ArrayNode mustArray = boolNode.putArray("must");
            mustArray.add(buildKeywordQuery(query.query()));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.set("bool", boolNode);
        return root;
    }

    private void appendFilters(SearchQuery query, ArrayNode filterArray) {
        ArrayNode statusValues = filterArray.addObject()
                .putObject("terms")
                .putArray("status");
        query.resolvedStatuses().forEach(statusValues::add);

        if (StringUtils.hasText(query.domainType())) {
            filterArray.addObject()
                    .putObject("term")
                    .put("domainType", query.domainType());
        }

        if (StringUtils.hasText(query.category())) {
            addCategoryFilter(query.category(), filterArray);
        }

        if (query.minPrice() != null || query.maxPrice() != null) {
            ObjectNode rangeNode = filterArray.addObject()
                    .putObject("range")
                    .putObject("price");
            if (query.minPrice() != null) {
                rangeNode.put("gte", query.minPrice());
            }
            if (query.maxPrice() != null) {
                rangeNode.put("lte", query.maxPrice());
            }
        }
    }

    private void addCategoryFilter(String category, ArrayNode filterArray) {
        if (isNumeric(category)) {
            filterArray.addObject()
                    .putObject("term")
                    .put("categoryId", Long.parseLong(category));
            return;
        }

        ObjectNode boolNode = filterArray.addObject().putObject("bool");
        ArrayNode shouldArray = boolNode.putArray("should");
        shouldArray.add(term("categoryCodes", normalizeCategoryCode(category)));
        shouldArray.add(matchPhrase("category", category));
        shouldArray.add(matchPhrase("categoryPath", category));
        boolNode.put("minimum_should_match", 1);
    }

    private ObjectNode buildKeywordQuery(String keyword) {
        ObjectNode boolNode = objectMapper.createObjectNode();
        ArrayNode shouldArray = boolNode.putArray("should");

        ArrayNode fields = objectMapper.createArrayNode();
        fields.add("title^6");
        fields.add("tags^5");
        fields.add("features^4");
        fields.add("detailHighlights^3");
        fields.add("category^2");
        fields.add("categoryPath^2");
        fields.add("storeName^2");
        fields.add("aiTags^3");
        fields.add("aiKeywords^2");
        fields.add("description^1.5");
        fields.add("aiSummary^1.5");
        fields.add("detailTitles^1.5");
        fields.add("detailDescriptions");

        ObjectNode multiMatch = objectMapper.createObjectNode();
        multiMatch.put("query", keyword);
        multiMatch.put("type", "best_fields");
        multiMatch.set("fields", fields);
        shouldArray.addObject().set("multi_match", multiMatch);
        shouldArray.add(term("categoryCodes", normalizeCategoryCode(keyword)));
        boolNode.put("minimum_should_match", 1);

        ObjectNode query = objectMapper.createObjectNode();
        query.set("bool", boolNode);
        return query;
    }

    private ArrayNode buildSort(SearchQuery query) {
        ArrayNode sortArray = objectMapper.createArrayNode();

        if (query.sortType().usesScore(query.hasKeyword())) {
            sortArray.add(sortField("_score", "desc", null));
            sortArray.add(sortField("sourceUpdatedAt", "desc", null));
            sortArray.add(sortField("itemId", "desc", null));
            return sortArray;
        }

        switch (query.sortType()) {
            case PRICE_ASC -> {
                sortArray.add(sortField("effectivePrice", "asc", "_last"));
                sortArray.add(sortField("itemId", "desc", null));
            }
            case PRICE_DESC -> {
                sortArray.add(sortField("effectivePrice", "desc", "_last"));
                sortArray.add(sortField("itemId", "desc", null));
            }
            case POPULAR, LATEST -> {
                sortArray.add(sortField("sourceUpdatedAt", "desc", null));
                sortArray.add(sortField("itemId", "desc", null));
            }
        }
        return sortArray;
    }

    private ObjectNode matchPhrase(String field, String value) {
        ObjectNode phrase = objectMapper.createObjectNode();
        phrase.put(field, value);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("match_phrase", phrase);
        return root;
    }

    private ObjectNode term(String field, String value) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put(field, value);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("term", body);
        return root;
    }

    private ObjectNode sortField(String field, String order, String missing) {
        ObjectNode sortWrapper = objectMapper.createObjectNode();
        ObjectNode sort = sortWrapper.putObject(field);
        sort.put("order", order);
        if (missing != null) {
            sort.put("missing", missing);
        }
        return sortWrapper;
    }

    private boolean hasNextPage(int offset, int returnedCount, Long totalCount) {
        if (totalCount == null) {
            return returnedCount > 0;
        }
        return offset + returnedCount < totalCount;
    }

    private Long extractTotalCount(JsonNode response) {
        JsonNode totalNode = response.path("hits").path("total");
        if (totalNode.isObject()) {
            return totalNode.path("value").isIntegralNumber()
                    ? totalNode.path("value").asLong()
                    : null;
        }
        return totalNode.isIntegralNumber() ? totalNode.asLong() : null;
    }

    private boolean isNumeric(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return false;
        }
        for (char ch : rawValue.toCharArray()) {
            if (!Character.isDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    private String normalizeCategoryCode(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}
