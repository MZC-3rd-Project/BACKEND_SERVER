package com.example.search.service.query.cache;

import com.example.search.dto.search.request.SearchRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchResultCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private Counter counter;

    private SearchResultCacheService searchResultCacheService;

    @BeforeEach
    void setUp() {
        searchResultCacheService = new SearchResultCacheService(stringRedisTemplate, meterRegistry);
    }

    @Test
    void get_returnsCachedValueWhenHit() {
        SearchRequest request = request("아이폰", List.of("SELLING", "READY"));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("search:cache:"))).thenReturn("{\"hits\":[]}");
        when(meterRegistry.counter("search.cache.requests", "result", "hit")).thenReturn(counter);

        Optional<String> cached = searchResultCacheService.get(request);

        assertThat(cached).contains("{\"hits\":[]}");
        verify(counter).increment();
    }

    @Test
    void get_returnsEmptyWhenMiss() {
        SearchRequest request = request("아이폰", List.of("SELLING"));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("search:cache:"))).thenReturn(null);
        when(meterRegistry.counter("search.cache.requests", "result", "miss")).thenReturn(counter);

        Optional<String> cached = searchResultCacheService.get(request);

        assertThat(cached).isEmpty();
        verify(counter).increment();
    }

    @Test
    void put_savesResponseWithTtlAndRegistersKey() {
        SearchRequest request = request("아이폰", List.of("READY", "SELLING"));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        searchResultCacheService.put(request, "{\"hits\":[]}");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq("{\"hits\":[]}"), eq(Duration.ofMinutes(5)));
        verify(setOperations).add("search:cache:keys", keyCaptor.getValue());
        verify(stringRedisTemplate).expire("search:cache:keys", Duration.ofMinutes(5));
    }

    @Test
    void evictAll_deletesRegisteredCacheKeys() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("search:cache:keys")).thenReturn(Set.of("search:cache:a", "search:cache:b"));

        searchResultCacheService.evictAll();

        verify(stringRedisTemplate).delete(Set.of("search:cache:a", "search:cache:b"));
        verify(stringRedisTemplate).delete("search:cache:keys");
    }

    private SearchRequest request(String q, List<String> statuses) {
        SearchRequest request = new SearchRequest();
        request.setQ(q);
        request.setStatus(statuses);
        request.setSort("LATEST");
        request.setSize(20);
        return request;
    }
}
