package com.example.config.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FallbackRegistry {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final Map<String, CachedResponse<?>> cache = new ConcurrentHashMap<>();

    public <T> void cacheResponse(String key, T response) {
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

    private record CachedResponse<T>(T response, Instant cachedAt) {}
}
