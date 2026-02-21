package com.example.search.service.query.cache;

import com.example.core.util.JsonUtils;
import com.example.search.dto.search.request.SearchRequest;
import com.example.search.util.SearchKeywordNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchResultCacheService {

    private static final String SEARCH_CACHE_PREFIX = "search:cache:";
    private static final String SEARCH_CACHE_KEY_SET = "search:cache:keys";
    private static final Duration SEARCH_CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;

    public Optional<String> get(SearchRequest request) {
        String cacheKey = cacheKey(request);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                meterRegistry.counter("search.cache.requests", "result", "hit").increment();
                log.info("Search cache hit. key={}", cacheKey);
                return Optional.of(cached);
            }
            meterRegistry.counter("search.cache.requests", "result", "miss").increment();
            log.info("Search cache miss. key={}", cacheKey);
            return Optional.empty();
        } catch (Exception e) {
            meterRegistry.counter("search.cache.requests", "result", "miss").increment();
            log.debug("Search cache lookup failed. key={}", cacheKey, e);
            return Optional.empty();
        }
    }

    public void put(SearchRequest request, String responseJson) {
        if (!StringUtils.hasText(responseJson)) {
            return;
        }

        String cacheKey = cacheKey(request);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, responseJson, SEARCH_CACHE_TTL);
            stringRedisTemplate.opsForSet().add(SEARCH_CACHE_KEY_SET, cacheKey);
            stringRedisTemplate.expire(SEARCH_CACHE_KEY_SET, SEARCH_CACHE_TTL);
        } catch (Exception e) {
            log.debug("Search cache save failed. key={}", cacheKey, e);
        }
    }

    public void evictAll() {
        try {
            Set<String> keys = stringRedisTemplate.opsForSet().members(SEARCH_CACHE_KEY_SET);
            if (CollectionUtils.isEmpty(keys)) {
                return;
            }

            stringRedisTemplate.delete(keys);
            stringRedisTemplate.delete(SEARCH_CACHE_KEY_SET);
            log.info("Search cache evicted. count={}", keys.size());
        } catch (Exception e) {
            log.debug("Search cache eviction failed.", e);
        }
    }

    private String cacheKey(SearchRequest request) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("q", SearchKeywordNormalizer.normalize(request.getQ()));
        normalized.put("category", normalizeText(request.getCategory()));
        normalized.put("domainType", normalizeDomainType(request.getDomainType()));
        normalized.put("status", normalizeStatuses(request.getStatus()));
        normalized.put("minPrice", request.getMinPrice());
        normalized.put("maxPrice", request.getMaxPrice());
        normalized.put("sort", normalizeSort(request.getSort()));
        normalized.put("cursor", normalizeText(request.getCursor()));
        normalized.put("size", request.getSize());

        String raw = JsonUtils.toJson(normalized);
        return SEARCH_CACHE_PREFIX + sha256(raw);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeDomainType(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSort(String value) {
        if (!StringUtils.hasText(value)) {
            return "LATEST";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeStatuses(List<String> statuses) {
        if (CollectionUtils.isEmpty(statuses)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String status : statuses) {
            if (!StringUtils.hasText(status)) {
                continue;
            }
            normalized.add(status.trim().toUpperCase(Locale.ROOT));
        }
        normalized.sort(String::compareTo);
        return normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
