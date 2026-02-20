package com.example.search.service.query.autocomplete;

import com.example.search.dto.autocomplete.request.AutocompleteRequest;
import com.example.search.dto.autocomplete.response.AutocompleteResponse;
import com.example.search.util.SearchKeywordNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutocompleteService {

    private static final String KEY_PREFIX = "autocomplete:";
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_PREFIX_LENGTH = 20;
    private static final Duration KEY_TTL = Duration.ofDays(7);

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

        try {
            Set<String> ranked = stringRedisTemplate.opsForZSet()
                    .reverseRange(prefixKey(normalized), 0, boundedSize - 1);

            List<String> suggestions = ranked == null ? List.of() : new ArrayList<>(ranked);
            return AutocompleteResponse.builder()
                    .suggestions(suggestions)
                    .build();
        } catch (Exception e) {
            log.debug("Autocomplete lookup failed. query={}", normalized, e);
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
            log.debug("Autocomplete record failed. keyword={}", normalized, e);
        }
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
}
