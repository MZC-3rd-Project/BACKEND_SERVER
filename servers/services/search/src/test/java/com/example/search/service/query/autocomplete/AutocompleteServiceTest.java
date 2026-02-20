package com.example.search.service.query.autocomplete;

import com.example.search.dto.autocomplete.request.AutocompleteRequest;
import com.example.search.dto.autocomplete.response.AutocompleteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutocompleteServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private AutocompleteService autocompleteService;

    @Test
    void recordKeyword_updatesPrefixKeysWithPopularity() {
        autocompleteService = new AutocompleteService(stringRedisTemplate);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        autocompleteService.recordKeyword(" 아이폰 케이스 ");

        verify(zSetOperations, atLeastOnce())
                .incrementScore(startsWith("autocomplete:"), eq("아이폰 케이스"), eq(1.0d));
        verify(stringRedisTemplate, atLeastOnce())
                .expire(startsWith("autocomplete:"), eq(Duration.ofDays(7)));
    }

    @Test
    void recordKeyword_ignoresBlankKeyword() {
        autocompleteService = new AutocompleteService(stringRedisTemplate);

        autocompleteService.recordKeyword("   ");

        verify(zSetOperations, never()).incrementScore(startsWith("autocomplete:"), eq(""), eq(1.0d));
    }

    @Test
    void suggest_returnsRankedSuggestions() {
        autocompleteService = new AutocompleteService(stringRedisTemplate);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<String> ranked = new LinkedHashSet<>();
        ranked.add("아이폰");
        ranked.add("아이폰 케이스");
        when(zSetOperations.reverseRange("autocomplete:아이", 0, 9)).thenReturn(ranked);

        AutocompleteResponse response = autocompleteService.suggest("아이", 10);

        assertThat(response.getSuggestions()).containsExactly("아이폰", "아이폰 케이스");
    }

    @Test
    void suggest_withRequestDto_returnsRankedSuggestions() {
        autocompleteService = new AutocompleteService(stringRedisTemplate);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Set<String> ranked = new LinkedHashSet<>();
        ranked.add("맥북");
        ranked.add("맥북 프로");
        when(zSetOperations.reverseRange("autocomplete:맥", 0, 4)).thenReturn(ranked);

        AutocompleteRequest request = new AutocompleteRequest();
        request.setQ("맥");
        request.setSize(5);
        AutocompleteResponse response = autocompleteService.suggest(request);

        assertThat(response.getSuggestions()).containsExactly("맥북", "맥북 프로");
    }

    @Test
    void suggest_returnsEmptyWhenQueryBlank() {
        autocompleteService = new AutocompleteService(stringRedisTemplate);

        AutocompleteResponse response = autocompleteService.suggest("  ", 10);

        assertThat(response.getSuggestions()).isEmpty();
        verify(zSetOperations, never()).reverseRange(startsWith("autocomplete:"), eq(0L), eq(9L));
    }
}
