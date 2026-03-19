package com.example.funding.service.cache;

import com.example.core.pagination.CursorResponse;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.entity.FundingStatus;
import com.example.funding.repository.FundingCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClosingSoonCampaignCacheService {

    static final String CACHE_KEY = "funding:main:closing-soon:v1";
    static final int DEFAULT_SIZE = 5;
    static final int MAX_SIZE = 20;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;
    private final FundingCampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public CursorResponse<CampaignResponse> getClosingSoonCampaigns(int size) {
        int safeSize = normalizeSize(size);
        ClosingSoonCampaignCachePayload cachedPayload = readPayload();
        if (cachedPayload != null && cachedPayload.getItems() != null && cachedPayload.getItems().size() >= safeSize) {
            return toCursorResponse(cachedPayload.getItems(), safeSize);
        }
        return refreshCache(safeSize);
    }

    @Transactional(readOnly = true)
    public CursorResponse<CampaignResponse> refreshDefaultCache() {
        return refreshCache(DEFAULT_SIZE);
    }

    @UseWriteDataSource
    @Transactional(readOnly = true)
    public CursorResponse<CampaignResponse> refreshCache(int size) {
        int safeSize = normalizeSize(size);
        List<ClosingSoonCampaignSnapshot> snapshots = campaignRepository.findClosingSoonActiveCampaigns(
                        FundingStatus.ACTIVE,
                        LocalDateTime.now(),
                        PageRequest.of(0, safeSize)
                ).stream()
                .map(ClosingSoonCampaignSnapshot::from)
                .toList();

        redisTemplate.opsForValue().set(
                CACHE_KEY,
                ClosingSoonCampaignCachePayload.builder()
                        .refreshedAt(LocalDateTime.now())
                        .items(snapshots)
                        .build(),
                CACHE_TTL
        );
        log.debug("closing soon funding cache refreshed. size={}", snapshots.size());
        return toCursorResponse(snapshots, safeSize);
    }

    private CursorResponse<CampaignResponse> toCursorResponse(List<ClosingSoonCampaignSnapshot> snapshots, int size) {
        if (snapshots == null || snapshots.isEmpty()) {
            return CursorResponse.empty();
        }
        List<CampaignResponse> content = snapshots.stream()
                .limit(size)
                .map(ClosingSoonCampaignSnapshot::toResponse)
                .toList();
        return CursorResponse.of(content, null);
    }

    private ClosingSoonCampaignCachePayload readPayload() {
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached instanceof ClosingSoonCampaignCachePayload payload) {
                return payload;
            }
            if (cached != null) {
                log.warn("unexpected closing soon cache payload type={}, evicting stale entry", cached.getClass().getName());
                redisTemplate.delete(CACHE_KEY);
            }
        } catch (Exception e) {
            log.warn("failed to read closing soon cache, evicting stale entry and rebuilding from DB", e);
            try {
                redisTemplate.delete(CACHE_KEY);
            } catch (Exception deleteException) {
                log.warn("failed to evict closing soon cache after read error", deleteException);
            }
        }
        return null;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
