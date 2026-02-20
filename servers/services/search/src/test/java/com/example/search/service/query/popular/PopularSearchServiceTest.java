package com.example.search.service.query.popular;

import com.example.search.dto.popular.response.PopularSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularSearchServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private PopularSearchService popularSearchService;

    @Test
    void recordKeyword_updatesPopularityAndLastSeen() {
        popularSearchService = new PopularSearchService(stringRedisTemplate);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        popularSearchService.recordKeyword(" 아이폰 ");

        verify(zSetOperations).incrementScore("popular:searches", "아이폰", 1.0d);
        verify(zSetOperations).add(eq("popular:searches:last-seen"), eq("아이폰"), anyDouble());
    }

    @Test
    void recordKeyword_ignoresBlankKeyword() {
        popularSearchService = new PopularSearchService(stringRedisTemplate);

        popularSearchService.recordKeyword("  ");

        verify(zSetOperations, never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    void getTopKeywords_returnsRankedKeywords() {
        popularSearchService = new PopularSearchService(stringRedisTemplate);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(new DefaultTypedTuple<>("아이폰", 12.0d));
        tuples.add(new DefaultTypedTuple<>("아이패드", 8.0d));
        when(zSetOperations.reverseRangeWithScores("popular:searches", 0, 9)).thenReturn(tuples);

        PopularSearchResponse response = popularSearchService.getTopKeywords();

        assertThat(response.getKeywords()).hasSize(2);
        assertThat(response.getKeywords().get(0).getKeyword()).isEqualTo("아이폰");
        assertThat(response.getKeywords().get(0).getCount()).isEqualTo(12L);
        assertThat(response.getKeywords().get(1).getKeyword()).isEqualTo("아이패드");
        assertThat(response.getKeywords().get(1).getCount()).isEqualTo(8L);
    }

    @Test
    void cleanupOldKeywords_removesExpiredMembers() {
        popularSearchService = new PopularSearchService(stringRedisTemplate);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<String> expired = new LinkedHashSet<>();
        expired.add("아이폰");
        expired.add("아이패드");
        when(zSetOperations.rangeByScore(eq("popular:searches:last-seen"), anyDouble(), anyDouble()))
                .thenReturn(expired);

        popularSearchService.cleanupOldKeywords();

        verify(zSetOperations).remove(eq("popular:searches"), eq("아이폰"), eq("아이패드"));
        verify(zSetOperations).remove(eq("popular:searches:last-seen"), eq("아이폰"), eq("아이패드"));
    }
}
