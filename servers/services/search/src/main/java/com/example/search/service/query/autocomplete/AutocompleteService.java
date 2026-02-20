package com.example.search.service.query.autocomplete;

import com.example.core.util.JsonUtils;
import com.example.search.document.ItemDocument;
import com.example.search.dto.autocomplete.request.AutocompleteRequest;
import com.example.search.dto.autocomplete.response.AutocompleteResponse;
import com.example.search.util.SearchKeywordNormalizer;
import com.example.search.util.SearchLogMasker;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutocompleteService {

    private static final String KEY_PREFIX = "autocomplete:";
    private static final Set<String> BLOCKED_EXPOSURE_STATUSES = Set.of(
            "DELETED", "HIDDEN", "PRIVATE", "SOLD_OUT", "ENDED", "INACTIVE"
    );
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_PREFIX_LENGTH = 20;
    private static final Duration KEY_TTL = Duration.ofDays(7);

    private final RestClient restClient;
    private final StringRedisTemplate stringRedisTemplate;

    public AutocompleteResponse suggest(AutocompleteRequest request) {
        if (request == null) {
            return AutocompleteResponse.builder().suggestions(List.of()).build();
        }
        int size = request.getSize() == null ? 10 : request.getSize();
        return suggest(request.getQ(), size);
    }

    public AutocompleteResponse suggest(String q, int size) {
        String normalized = SearchKeywordNormalizer.normalize(q);
        if (!StringUtils.hasText(normalized)) {
            return AutocompleteResponse.builder().suggestions(List.of()).build();
        }

        int boundedSize = Math.max(1, Math.min(size, 20));
        List<String> redisSuggestions = suggestFromRedis(normalized, boundedSize);
        List<String> elasticSuggestions = List.of();

        try {
            elasticSuggestions = suggestFromElasticsearch(normalized, boundedSize);
        } catch (Exception e) {
            log.debug("Autocomplete elasticsearch lookup failed. queryHash={}", SearchLogMasker.keywordHash(normalized), e);
        }

        try {
            if (!elasticSuggestions.isEmpty() && !redisSuggestions.isEmpty() && !elasticSuggestions.equals(redisSuggestions)) {
                log.debug("Autocomplete ES/Redis diff. queryHash={}, esSize={}, redisSize={}",
                        SearchLogMasker.keywordHash(normalized),
                        elasticSuggestions.size(),
                        redisSuggestions.size());
            }
            if (!elasticSuggestions.isEmpty()) {
                return AutocompleteResponse.builder().suggestions(elasticSuggestions).build();
            }
            return AutocompleteResponse.builder().suggestions(redisSuggestions).build();
        } catch (Exception e) {
            log.debug("Autocomplete lookup failed. queryHash={}", SearchLogMasker.keywordHash(normalized), e);
            return AutocompleteResponse.builder().suggestions(List.of()).build();
        }
    }

    public void recordKeyword(String keyword) {
        String normalized = SearchKeywordNormalizer.normalize(keyword);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            return;
        }

        List<String> prefixes = buildPrefixes(normalized);
        if (prefixes.isEmpty()) {
            return;
        }

        try {
            for (String prefix : prefixes) {
                String key = prefixKey(prefix);
                stringRedisTemplate.opsForZSet().incrementScore(key, normalized, 1.0d);
                stringRedisTemplate.expire(key, KEY_TTL);
            }
        } catch (Exception e) {
            log.debug("Autocomplete record failed. keywordHash={}", SearchLogMasker.keywordHash(normalized), e);
        }
    }

    private List<String> suggestFromElasticsearch(String normalized, int boundedSize) throws IOException {
        Map<String, Object> boolQuery = Map.of(
                "should", List.of(
                        Map.of("match", Map.of("title.autocomplete", Map.of("query", normalized, "operator", "and"))),
                        Map.of("prefix", Map.of("title.keyword", Map.of("value", normalized))),
                        Map.of("match_phrase_prefix", Map.of("title", Map.of("query", normalized)))
                ),
                "minimum_should_match", 1,
                "must_not", List.of(Map.of("terms", Map.of("status", BLOCKED_EXPOSURE_STATUSES)))
        );

        Map<String, Object> body = Map.of(
                "size", boundedSize * 3,
                "_source", List.of("title"),
                "query", Map.of("bool", boolQuery),
                "sort", List.of(
                        Map.of("_score", Map.of("order", "desc")),
                        Map.of("createdAt", Map.of("order", "desc")),
                        Map.of("itemId", Map.of("order", "desc"))
                )
        );

        Request request = new Request("POST", "/" + ItemDocument.ITEMS_READ_ALIAS + "/_search");
        request.setJsonEntity(JsonUtils.toJson(body));

        Response response = restClient.performRequest(request);
        String json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        Map<String, Object> root = JsonUtils.fromJson(json, new TypeReference<>() {
        });
        Map<String, Object> hits = toMap(root.get("hits"));
        List<Map<String, Object>> hitList = toMapList(hits.get("hits"));

        Set<String> suggestions = new LinkedHashSet<>();
        for (Map<String, Object> hit : hitList) {
            Map<String, Object> source = toMap(hit.get("_source"));
            String title = asString(source.get("title"));
            if (!StringUtils.hasText(title)) {
                continue;
            }
            suggestions.add(title);
            if (suggestions.size() >= boundedSize) {
                break;
            }
        }
        return new ArrayList<>(suggestions);
    }

    private List<String> suggestFromRedis(String normalized, int boundedSize) {
        Set<String> ranked = stringRedisTemplate.opsForZSet()
                .reverseRange(prefixKey(normalized), 0, boundedSize - 1);
        if (ranked == null) {
            return List.of();
        }
        return new ArrayList<>(ranked);
    }

    private List<String> buildPrefixes(String keyword) {
        int length = Math.min(keyword.length(), MAX_PREFIX_LENGTH);
        Set<String> uniquePrefixes = new LinkedHashSet<>();
        for (int i = 1; i <= length; i++) {
            uniquePrefixes.add(keyword.substring(0, i));
        }
        return new ArrayList<>(uniquePrefixes);
    }

    private String prefixKey(String prefix) {
        return KEY_PREFIX + prefix;
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
