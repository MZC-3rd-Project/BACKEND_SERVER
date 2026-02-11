package com.example.funding.service;

import com.example.funding.dto.campaign.response.ProgressResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.repository.FundingCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignCacheService {

    private static final String KEY_PREFIX = "funding:progress:";
    private static final String FIELD_CURRENT_AMOUNT = "currentAmount";
    private static final String FIELD_CURRENT_QUANTITY = "currentQuantity";
    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Object> redisTemplate;
    private final FundingCampaignRepository campaignRepository;

    public void cacheProgress(FundingCampaign campaign) {
        String key = KEY_PREFIX + campaign.getId();
        redisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_CURRENT_AMOUNT, campaign.getCurrentAmount(),
                FIELD_CURRENT_QUANTITY, campaign.getCurrentQuantity()
        ));
        redisTemplate.expire(key, TTL);
    }

    public void incrementProgress(Long campaignId, Long amount, int quantity) {
        String key = KEY_PREFIX + campaignId;
        try {
            redisTemplate.opsForHash().increment(key, FIELD_CURRENT_AMOUNT, amount);
            redisTemplate.opsForHash().increment(key, FIELD_CURRENT_QUANTITY, quantity);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("Redis increment failed for campaign {}, will rely on DB", campaignId, e);
        }
    }

    public void decrementProgress(Long campaignId, Long amount, int quantity) {
        incrementProgress(campaignId, -amount, -quantity);
    }

    public ProgressResponse getProgress(Long campaignId) {
        String key = KEY_PREFIX + campaignId;

        try {
            Object amountObj = redisTemplate.opsForHash().get(key, FIELD_CURRENT_AMOUNT);
            Object quantityObj = redisTemplate.opsForHash().get(key, FIELD_CURRENT_QUANTITY);

            if (amountObj != null && quantityObj != null) {
                FundingCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
                if (campaign == null) return null;

                long currentAmount = ((Number) amountObj).longValue();
                int currentQuantity = ((Number) quantityObj).intValue();

                return ProgressResponse.of(campaignId, currentAmount, campaign.getGoalAmount(),
                        currentQuantity, campaign.getGoalQuantity());
            }
        } catch (Exception e) {
            log.warn("Redis get failed for campaign {}, falling back to DB", campaignId, e);
        }

        return getProgressFromDb(campaignId);
    }

    public void invalidateProgress(Long campaignId) {
        redisTemplate.delete(KEY_PREFIX + campaignId);
    }

    private ProgressResponse getProgressFromDb(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .map(c -> {
                    cacheProgress(c);
                    return ProgressResponse.of(campaignId, c.getCurrentAmount(), c.getGoalAmount(),
                            c.getCurrentQuantity(), c.getGoalQuantity());
                })
                .orElse(null);
    }
}
