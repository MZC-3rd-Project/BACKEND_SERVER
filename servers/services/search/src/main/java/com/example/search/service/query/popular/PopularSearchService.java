package com.example.search.service.query.popular;

import com.example.search.config.SearchPopularProperties;
import com.example.search.dto.popular.response.PopularSearchResponse;
import com.example.search.util.SearchKeywordNormalizer;
import com.example.search.util.SearchLogMasker;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularSearchService {

    private static final String POPULAR_KEY = "popular:searches";
    private static final String LAST_SEEN_KEY = "popular:searches:last-seen";
    private static final String BLOCKED_KEYWORDS_KEY = "popular:blocked-keywords";
    private static final String RATE_LIMIT_SECOND_PREFIX = "popular:rl:sec:";
    private static final String RATE_LIMIT_MINUTE_PREFIX = "popular:rl:min:";
    private static final String REPEAT_KEYWORD_PREFIX = "popular:repeat:";
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int TOP_LIMIT = 10;
    private static final Duration RETENTION = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final SearchRequestIdentityResolver identityResolver;
    private final SearchPopularProperties popularProperties;

    public void recordKeyword(String keyword) {
        String normalized = SearchKeywordNormalizer.normalize(keyword);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            return;
        }
        if (isBlockedKeyword(normalized)) {
            return;
        }

        String identity = identityResolver.resolveIdentity();
        if (isRateLimited(identity)) {
            return;
        }

        double scoreDelta = calculateScoreDelta(identity, normalized);
        if (scoreDelta <= 0) {
            return;
        }

        try {
            ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
            zSet.incrementScore(POPULAR_KEY, normalized, scoreDelta);
            zSet.add(LAST_SEEN_KEY, normalized, (double) System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("Popular keyword record failed. keywordHash={}", SearchLogMasker.keywordHash(normalized), e);
        }
    }

    public PopularSearchResponse getTopKeywords() {
        try {
            ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
            Set<ZSetOperations.TypedTuple<String>> tuples = zSet.reverseRangeWithScores(POPULAR_KEY, 0, TOP_LIMIT * 5L);
            if (CollectionUtils.isEmpty(tuples)) {
                return PopularSearchResponse.builder()
                        .keywords(List.of())
                        .build();
            }

            Set<String> blockedKeywords = resolveBlockedKeywords();
            List<PopularSearchResponse.KeywordCount> keywords = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String keyword = tuple.getValue();
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                if (containsBlockedKeyword(keyword, blockedKeywords)) {
                    continue;
                }
                long count = tuple.getScore() == null ? 0L : Math.round(tuple.getScore());
                keywords.add(PopularSearchResponse.KeywordCount.builder()
                        .keyword(keyword)
                        .count(count)
                        .build());
                if (keywords.size() == TOP_LIMIT) {
                    break;
                }
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

    public List<String> getBlockedKeywords() {
        return new ArrayList<>(resolveBlockedKeywords());
    }

    public List<String> addBlockedKeyword(String keyword) {
        String normalized = SearchKeywordNormalizer.normalize(keyword);
        if (!StringUtils.hasText(normalized)) {
            return getBlockedKeywords();
        }
        stringRedisTemplate.opsForSet().add(BLOCKED_KEYWORDS_KEY, normalized);
        return getBlockedKeywords();
    }

    public List<String> removeBlockedKeyword(String keyword) {
        String normalized = SearchKeywordNormalizer.normalize(keyword);
        if (!StringUtils.hasText(normalized)) {
            return getBlockedKeywords();
        }
        stringRedisTemplate.opsForSet().remove(BLOCKED_KEYWORDS_KEY, normalized);
        return getBlockedKeywords();
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

    private boolean isRateLimited(String identity) {
        long nowMillis = System.currentTimeMillis();
        String secondKey = RATE_LIMIT_SECOND_PREFIX + identity + ":" + (nowMillis / 1000);
        String minuteKey = RATE_LIMIT_MINUTE_PREFIX + identity + ":" + (nowMillis / 60000);

        Long perSecondCount = incrementWithTtl(secondKey, Duration.ofSeconds(2));
        Long perMinuteCount = incrementWithTtl(minuteKey, Duration.ofMinutes(2));

        return perSecondCount > popularProperties.getPerSecondLimit()
                || perMinuteCount > popularProperties.getPerMinuteLimit();
    }

    private double calculateScoreDelta(String identity, String normalizedKeyword) {
        String repeatKey = REPEAT_KEYWORD_PREFIX + identity + ":" + normalizedKeyword;
        Long repeatedCount = incrementWithTtl(repeatKey, Duration.ofMinutes(1));
        if (repeatedCount > popularProperties.getRepeatRejectThreshold()) {
            return 0.0d;
        }
        if (repeatedCount > popularProperties.getRepeatPenaltyThreshold()) {
            return 0.2d;
        }
        return 1.0d;
    }

    private Long incrementWithTtl(String key, Duration ttl) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, ttl);
        }
        return count == null ? 0L : count;
    }

    private boolean isBlockedKeyword(String keyword) {
        return containsBlockedKeyword(keyword, resolveBlockedKeywords());
    }

    private boolean containsBlockedKeyword(String keyword, Set<String> blockedKeywords) {
        if (!StringUtils.hasText(keyword) || CollectionUtils.isEmpty(blockedKeywords)) {
            return false;
        }
        String normalized = SearchKeywordNormalizer.normalize(keyword);
        for (String blocked : blockedKeywords) {
            if (normalized.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> resolveBlockedKeywords() {
        Set<String> resolved = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(popularProperties.getBlockedKeywords())) {
            resolved.addAll(popularProperties.getBlockedKeywords().stream()
                    .map(SearchKeywordNormalizer::normalize)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet()));
        }

        Set<String> redisBlocked = stringRedisTemplate.opsForSet().members(BLOCKED_KEYWORDS_KEY);
        if (!CollectionUtils.isEmpty(redisBlocked)) {
            resolved.addAll(redisBlocked.stream()
                    .map(SearchKeywordNormalizer::normalize)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet()));
        }
        return resolved;
    }
}
