package com.example.config.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FallbackRegistry {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHE_SIZE = 1000;

    private final Map<String, CachedResponse<?>> cache = new ConcurrentHashMap<>();

    public <T> void cacheResponse(String key, T response) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            evictExpiredEntries();
        }
        if (cache.size() >= MAX_CACHE_SIZE) {
            log.warn("[FallbackRegistry] Cache full (max={}), skipping cache for: {}", MAX_CACHE_SIZE, key);
            return;
        }
        cache.put(key, new CachedResponse<>(response, Instant.now()));
        log.debug("[FallbackRegistry] Cached response for: {}", key);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCachedResponse(String key) {
        CachedResponse<?> cached = cache.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (Duration.between(cached.cachedAt(), Instant.now()).compareTo(DEFAULT_TTL) > 0) {
            cache.remove(key);
            log.debug("[FallbackRegistry] Expired cache for: {}", key);
            return Optional.empty();
        }
        return Optional.of((T) cached.response());
    }

    public void evict(String key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    @Scheduled(fixedRate = 60000)
    public void evictExpiredEntries() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, CachedResponse<?>>> it = cache.entrySet().iterator();
        int evicted = 0;
        while (it.hasNext()) {
            Map.Entry<String, CachedResponse<?>> entry = it.next();
            if (Duration.between(entry.getValue().cachedAt(), now).compareTo(DEFAULT_TTL) > 0) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("[FallbackRegistry] Evicted {} expired entries", evicted);
        }
    }

    private record CachedResponse<T>(T response, Instant cachedAt) {}
}
