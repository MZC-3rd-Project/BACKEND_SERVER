package com.example.search.service.query.popular;

import com.example.search.dto.popular.response.PopularSearchResponse;
import com.example.search.util.SearchKeywordNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularSearchService {

    private static final String POPULAR_KEY = "popular:searches";
    private static final String LAST_SEEN_KEY = "popular:searches:last-seen";
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int TOP_LIMIT = 10;
    private static final Duration RETENTION = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;

    public void recordKeyword(String keyword) {
        String normalized = SearchKeywordNormalizer.normalize(keyword);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            return;
        }

        try {
            ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
            zSet.incrementScore(POPULAR_KEY, normalized, 1.0d);
            zSet.add(LAST_SEEN_KEY, normalized, (double) System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("Popular keyword record failed. keyword={}", normalized, e);
        }
    }

    public PopularSearchResponse getTopKeywords() {
        try {
            ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
            Set<ZSetOperations.TypedTuple<String>> tuples = zSet.reverseRangeWithScores(POPULAR_KEY, 0, TOP_LIMIT - 1);
            if (CollectionUtils.isEmpty(tuples)) {
                return PopularSearchResponse.builder()
                        .keywords(List.of())
                        .build();
            }

            List<PopularSearchResponse.KeywordCount> keywords = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String keyword = tuple.getValue();
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                long count = tuple.getScore() == null ? 0L : Math.round(tuple.getScore());
                keywords.add(PopularSearchResponse.KeywordCount.builder()
                        .keyword(keyword)
                        .count(count)
                        .build());
            }

            return PopularSearchResponse.builder()
                    .keywords(keywords)
                    .build();
        } catch (Exception e) {
            log.debug("Popular keyword lookup failed.", e);
            return PopularSearchResponse.builder()
                    .keywords(List.of())
                    .build();
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOldKeywords() {
        long cutoffMillis = System.currentTimeMillis() - RETENTION.toMillis();
        try {
            ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
            Set<String> expiredKeywords = zSet.rangeByScore(LAST_SEEN_KEY, Double.NEGATIVE_INFINITY, cutoffMillis);
            if (CollectionUtils.isEmpty(expiredKeywords)) {
                return;
            }

            Object[] members = expiredKeywords.toArray();
            zSet.remove(POPULAR_KEY, members);
            zSet.remove(LAST_SEEN_KEY, members);
        } catch (Exception e) {
            log.debug("Popular keyword cleanup failed.", e);
        }
    }
}
