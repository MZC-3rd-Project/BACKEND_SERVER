package com.example.funding.scheduler;

import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingStatus;
import com.example.funding.entity.FundingStatusHistory;
import com.example.funding.event.FundingFailedEvent;
import com.example.funding.event.FundingSucceededEvent;
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
    private final EventPublisher eventPublisher;

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

        // 도메인 이벤트 발행
        if (newStatus == FundingStatus.SUCCEEDED) {
            eventPublisher.publish(
                    new FundingSucceededEvent(
                            campaign.getId(), campaign.getItemId(), campaign.getSellerId(),
                            campaign.getFundingType().name(), campaign.getGoalAmount(),
                            campaign.getCurrentAmount(), campaign.getCurrentQuantity()
                    ),
                    EventMetadata.of("FundingCampaign", String.valueOf(campaign.getId()))
            );
        } else {
            eventPublisher.publish(
                    new FundingFailedEvent(
                            campaign.getId(), campaign.getItemId(), campaign.getSellerId(),
                            campaign.getFundingType().name(), campaign.getGoalAmount(),
                            campaign.getCurrentAmount(), campaign.getCurrentQuantity()
                    ),
                    EventMetadata.of("FundingCampaign", String.valueOf(campaign.getId()))
            );
        }

        log.info("캠페인 판정 완료: campaignId={}, {} → {}",
                campaign.getId(), previousStatus, newStatus);
    }
}
