package com.example.funding.scheduler;

import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingStatus;
import com.example.funding.entity.FundingStatusHistory;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingStatusHistoryRepository;
import com.example.funding.service.CampaignCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundingDeadlineScheduler {

    private final FundingCampaignRepository campaignRepository;
    private final FundingStatusHistoryRepository statusHistoryRepository;
    private final CampaignCacheService campaignCacheService;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void judgeExpiredCampaigns() {
        List<FundingCampaign> expired = campaignRepository
                .findExpiredCampaigns(FundingStatus.ACTIVE, LocalDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("만료된 펀딩 캠페인 {} 건 판정 시작", expired.size());

        for (FundingCampaign campaign : expired) {
            try {
                judgeCampaign(campaign);
            } catch (Exception e) {
                log.error("캠페인 판정 실패: campaignId={}", campaign.getId(), e);
            }
        }
    }

    private void judgeCampaign(FundingCampaign campaign) {
        FundingStatus previousStatus = campaign.getStatus();
        FundingStatus newStatus;
        String reason;

        if (campaign.isGoalReached()) {
            newStatus = FundingStatus.SUCCEEDED;
            reason = String.format("목표 달성 (현재: %d원 / 목표: %d원)",
                    campaign.getCurrentAmount(), campaign.getGoalAmount());
        } else {
            newStatus = FundingStatus.FAILED;
            reason = String.format("목표 미달 (현재: %d원 / 목표: %d원)",
                    campaign.getCurrentAmount(), campaign.getGoalAmount());
        }

        campaign.changeStatus(newStatus);

        statusHistoryRepository.save(
                FundingStatusHistory.create(campaign.getId(), previousStatus, newStatus, reason)
        );

        campaignCacheService.invalidateProgress(campaign.getId());

        log.info("캠페인 판정 완료: campaignId={}, {} → {}",
                campaign.getId(), previousStatus, newStatus);
    }
}
