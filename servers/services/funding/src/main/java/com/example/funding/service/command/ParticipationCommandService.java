package com.example.funding.service.command;

import com.example.core.exception.BusinessException;
import com.example.funding.client.StockClient;
import com.example.funding.dto.participation.request.ParticipateRequest;
import com.example.funding.dto.participation.response.ParticipationResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.entity.FundingType;
import com.example.funding.exception.FundingErrorCode;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingParticipationRepository;
import com.example.funding.service.CampaignCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ParticipationCommandService {

    private final FundingCampaignRepository campaignRepository;
    private final FundingParticipationRepository participationRepository;
    private final StockClient stockClient;
    private final CampaignCacheService campaignCacheService;

    public ParticipationResponse participate(Long campaignId, ParticipateRequest request, Long userId) {
        FundingCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));

        if (!campaign.isActive()) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
        }

        if (campaign.isExpired()) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
        }

        if (campaign.getFundingType() == FundingType.AMOUNT_BASED) {
            return participateAmountBased(campaign, request, userId);
        } else {
            return participateQuantityBased(campaign, request, userId);
        }
    }

    public void refund(Long participationId, Long userId) {
        FundingParticipation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.PARTICIPATION_NOT_FOUND));

        FundingCampaign campaign = campaignRepository.findById(participation.getCampaignId())
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));

        if (!campaign.isActive()) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
        }

        // Stock 예약 취소 (QUANTITY_BASED만)
        if (participation.getReservationId() != null) {
            stockClient.cancelReservation(participation.getReservationId());
        }

        participation.refund();
        campaign.removeParticipation(participation.getAmount(), participation.getQuantity());
        campaignCacheService.decrementProgress(campaign.getId(),
                participation.getAmount(), participation.getQuantity());
    }

    private ParticipationResponse participateAmountBased(FundingCampaign campaign,
                                                          ParticipateRequest request, Long userId) {
        // 최소 금액 검증
        if (campaign.getMinAmount() != null && request.getAmount() < campaign.getMinAmount()) {
            throw new BusinessException(FundingErrorCode.BELOW_MIN_AMOUNT);
        }

        FundingParticipation participation = FundingParticipation.create(
                campaign.getId(), userId, request.getAmount(),
                1, null, null, null
        );

        participationRepository.save(participation);
        campaign.addParticipation(request.getAmount(), 1);
        campaignCacheService.incrementProgress(campaign.getId(), request.getAmount(), 1);

        return ParticipationResponse.from(participation);
    }

    private ParticipationResponse participateQuantityBased(FundingCampaign campaign,
                                                            ParticipateRequest request, Long userId) {
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        Long referenceId = request.getSeatGradeId() != null
                ? request.getSeatGradeId()
                : request.getItemOptionId();

        // Stock TCC Reserve
        Long stockItemId = stockClient.findStockItemId(campaign.getItemId(), referenceId);
        Long reservationId = stockClient.reserveStock(stockItemId, userId, quantity);

        FundingParticipation participation = FundingParticipation.create(
                campaign.getId(), userId, request.getAmount(),
                quantity, request.getSeatGradeId(), request.getItemOptionId(), reservationId
        );

        participationRepository.save(participation);
        campaign.addParticipation(request.getAmount(), quantity);
        campaignCacheService.incrementProgress(campaign.getId(), request.getAmount(), quantity);

        return ParticipationResponse.from(participation);
    }
}
