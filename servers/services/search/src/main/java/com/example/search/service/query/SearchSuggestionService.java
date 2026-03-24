package com.example.search.service.query;

import com.example.search.dto.response.SearchSuggestionResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchSuggestionService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private static final List<String> PUBLIC_STATUSES = List.of("ON_SALE", "FUNDING", "FUNDED", "HOT_DEAL");

    private final ObjectMapper objectMapper;
    private final ElasticsearchDocumentClient elasticsearchDocumentClient;

    public List<SearchSuggestionResponse> suggest(String rawQuery, Integer rawSize) {
        if (!StringUtils.hasText(rawQuery)) {
            return List.of();
        }

        String query = rawQuery.trim();
        int size = normalizeSize(rawSize);
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("size", Math.min(size * 5, 50));
        requestBody.put("track_total_hits", false);
        ArrayNode sourceArray = requestBody.putArray("_source");
        sourceArray.add("title");
        sourceArray.add("tags");
        sourceArray.add("storeName");
        sourceArray.add("category");
        sourceArray.add("categoryPath");
        requestBody.set("query", buildSuggestionQuery(query));
        requestBody.set("sort", buildSort());

        JsonNode response = elasticsearchDocumentClient.search(requestBody);
        JsonNode hitNodes = response.path("hits").path("hits");
        if (!hitNodes.isArray()) {
            return List.of();
        }

        Map<String, SearchSuggestionResponse> suggestions = new LinkedHashMap<>();
        for (JsonNode hitNode : hitNodes) {
            JsonNode source = hitNode.path("_source");
            if (!source.isObject()) {
                continue;
            }

            addSuggestion(suggestions, source.path("title").asText(null), "TITLE", query, size);
            addSuggestions(suggestions, source.path("tags"), "TAG", query, size);
            addSuggestion(suggestions, source.path("storeName").asText(null), "STORE", query, size);
            addSuggestion(suggestions, source.path("category").asText(null), "CATEGORY", query, size);
            addSuggestions(suggestions, source.path("categoryPath"), "CATEGORY", query, size);

            if (suggestions.size() >= size) {
                break;
            }
        }

        return List.copyOf(new ArrayList<>(suggestions.values()));
    }

    private ObjectNode buildSuggestionQuery(String query) {
        ObjectNode boolNode = objectMapper.createObjectNode();
        ArrayNode filterArray = boolNode.putArray("filter");
        ArrayNode statusValues = filterArray.addObject()
                .putObject("terms")
                .putArray("status");
        PUBLIC_STATUSES.forEach(statusValues::add);

        ArrayNode shouldArray = boolNode.putArray("should");
        shouldArray.add(matchPhrasePrefix("title", query));
        shouldArray.add(matchPhrasePrefix("tags", query));
        shouldArray.add(matchPhrasePrefix("storeName", query));
        shouldArray.add(matchPhrasePrefix("category", query));
        shouldArray.add(matchPhrasePrefix("categoryPath", query));
        boolNode.put("minimum_should_match", 1);

        ObjectNode queryNode = objectMapper.createObjectNode();
        queryNode.set("bool", boolNode);
        return queryNode;
    }

    private ArrayNode buildSort() {
        ArrayNode sortArray = objectMapper.createArrayNode();
        sortArray.add(sortField("_score", "desc"));
        sortArray.add(sortField("sourceUpdatedAt", "desc"));
        sortArray.add(sortField("itemId", "desc"));
        return sortArray;
    }

    private ObjectNode matchPhrasePrefix(String field, String value) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put(field, value);
        ObjectNode node = objectMapper.createObjectNode();
        node.set("match_phrase_prefix", body);
        return node;
    }

    private ObjectNode sortField(String field, String order) {
        ObjectNode sortWrapper = objectMapper.createObjectNode();
        sortWrapper.putObject(field).put("order", order);
        return sortWrapper;
    }

    private void addSuggestions(
            Map<String, SearchSuggestionResponse> suggestions,
            JsonNode valuesNode,
            String type,
            String query,
            int limit
    ) {
        if (!valuesNode.isArray()) {
            return;
        }
        for (JsonNode valueNode : valuesNode) {
            addSuggestion(suggestions, valueNode.asText(null), type, query, limit);
            if (suggestions.size() >= limit) {
                return;
            }
        }
    }

    private void addSuggestion(
            Map<String, SearchSuggestionResponse> suggestions,
            String rawValue,
            String type,
            String query,
            int limit
    ) {
        if (suggestions.size() >= limit || !StringUtils.hasText(rawValue)) {
            return;
        }

        String normalizedValue = rawValue.trim();
        if (!matchesSuggestion(normalizedValue, query)) {
            return;
        }

        suggestions.putIfAbsent(
                normalizedValue.toLowerCase(Locale.ROOT),
                new SearchSuggestionResponse(normalizedValue, type)
        );
    }

    private boolean matchesSuggestion(String candidate, String query) {
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return normalizedCandidate.contains(normalizedQuery);
    }

    private int normalizeSize(Integer rawSize) {
        if (rawSize == null || rawSize <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(rawSize, MAX_SIZE);
    }
}
