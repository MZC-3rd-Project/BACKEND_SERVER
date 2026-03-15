package com.example.hotdeal.service.query;

import com.example.hotdeal.dto.query.response.HotDealDetailQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotDealDetailCacheTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private HotDealDetailCache hotDealDetailCache;

    @BeforeEach
    void setUp() {
        hotDealDetailCache = new HotDealDetailCache(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void get_returnsCachedResponseWhenPresent() {
        HotDealDetailQueryResponse response = HotDealDetailQueryResponse.builder()
                .id(10L)
                .title("deal")
                .build();
        when(valueOperations.get("hotdeal:detail:10")).thenReturn(response);

        Optional<HotDealDetailQueryResponse> cached = hotDealDetailCache.get(10L);

        assertThat(cached).containsSame(response);
    }

    @Test
    void get_returnsEmptyWhenCachedValueHasUnexpectedType() {
        when(valueOperations.get("hotdeal:detail:10")).thenReturn("not-a-response");

        Optional<HotDealDetailQueryResponse> cached = hotDealDetailCache.get(10L);

        assertThat(cached).isEmpty();
    }

    @Test
    void put_cachesResponseWithExpectedKeyAndTtl() {
        HotDealDetailQueryResponse response = HotDealDetailQueryResponse.builder()
                .id(10L)
                .title("deal")
                .build();

        hotDealDetailCache.put(10L, response);

        verify(valueOperations).set("hotdeal:detail:10", response, 10L, TimeUnit.SECONDS);
    }
}
