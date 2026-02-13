package com.example.funding.service.command;

import com.example.core.exception.BusinessException;
import com.example.core.id.Snowflake;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.funding.client.StockClient;
import com.example.funding.dto.participation.request.ParticipateRequest;
import com.example.funding.dto.participation.response.ParticipationResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.entity.FundingStatus;
import com.example.funding.entity.FundingType;
import com.example.funding.event.FundingParticipatedEvent;
import com.example.funding.event.FundingRefundedEvent;
import com.example.funding.exception.FundingErrorCode;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingParticipationRepository;
import com.example.funding.service.CampaignCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationCommandService {

    private final FundingCampaignRepository campaignRepository;
    private final FundingParticipationRepository participationRepository;
    private final StockClient stockClient;
    private final CampaignCacheService campaignCacheService;
    private final EventPublisher eventPublisher;
    private final Snowflake snowflake;
    private final TransactionTemplate transactionTemplate;

    public ParticipationResponse participate(Long campaignId, ParticipateRequest request, Long userId) {
        FundingCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));

        if (!campaign.isActive()) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
        }

        if (campaign.isExpired()) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
        }

        Long orderId = snowflake.nextId();

        if (campaign.getFundingType() == FundingType.AMOUNT_BASED) {
            return transactionTemplate.execute(status ->
                    participateAmountBased(campaignId, request, userId, orderId));
        } else {
            return participateQuantityBased(campaign, campaignId, request, userId, orderId);
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

        if (participation.getReservationId() != null) {
            stockClient.cancelReservation(participation.getReservationId());
        }
        transactionTemplate.executeWithoutResult(status -> refundInTransaction(participationId, userId));
    }

    private void refundInTransaction(Long participationId, Long userId) {
        FundingParticipation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.PARTICIPATION_NOT_FOUND));

        FundingCampaign campaign = campaignRepository.findById(participation.getCampaignId())
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));

        if (!campaign.isActive()) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
        }

        participation.refund();
        campaignRepository.decrementParticipation(campaign.getId(),
                participation.getAmount(), participation.getQuantity());
        campaignCacheService.decrementProgress(campaign.getId(),
                participation.getAmount(), participation.getQuantity());

        eventPublisher.publish(
                new FundingRefundedEvent(
                        campaign.getId(), participation.getId(), participation.getOrderId(),
                        userId, participation.getAmount()
                ),
                EventMetadata.of("FundingCampaign", String.valueOf(campaign.getId()))
        );
    }

    private ParticipationResponse participateAmountBased(Long campaignId,
                                                          ParticipateRequest request, Long userId, Long orderId) {
        FundingCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));

        if (campaign.getMinAmount() != null && request.getAmount() < campaign.getMinAmount()) {
            throw new BusinessException(FundingErrorCode.BELOW_MIN_AMOUNT);
        }

        FundingParticipation participation = FundingParticipation.create(
                campaign.getId(), userId, request.getAmount(),
                1, null, null, orderId, null
        );

        participationRepository.save(participation);
        int updated = campaignRepository.incrementParticipationIfAvailable(
                campaign.getId(),
                request.getAmount(),
                1,
                FundingStatus.ACTIVE,
                LocalDateTime.now()
        );
        if (updated == 0) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
        }
        campaignCacheService.incrementProgress(campaign.getId(), request.getAmount(), 1);

        eventPublisher.publish(
                new FundingParticipatedEvent(
                        campaign.getId(), participation.getId(), orderId,
                        userId, request.getAmount(), 1, campaign.getFundingType().name()
                ),
                EventMetadata.of("FundingCampaign", String.valueOf(campaign.getId()))
        );

        return ParticipationResponse.from(participation);
    }

    private ParticipationResponse participateQuantityBased(FundingCampaign campaign, Long campaignId,
                                                            ParticipateRequest request, Long userId, Long orderId) {
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        Long referenceId = request.getSeatGradeId() != null
                ? request.getSeatGradeId()
                : request.getItemOptionId();

        // HTTP calls OUTSIDE transaction
        Long stockItemId = stockClient.findStockItemId(campaign.getItemId(), referenceId);
        Long reservationId = stockClient.reserveStock(stockItemId, userId, quantity, orderId);

        try {
            // DB operations INSIDE transaction
            return transactionTemplate.execute(status -> {
                FundingCampaign c = campaignRepository.findByIdWithLock(campaignId)
                        .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));

                if (!c.isActive()) {
                    throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_ACTIVE);
                }
                if (c.getGoalQuantity() != null && c.getCurrentQuantity() + quantity > c.getGoalQuantity()) {
                    throw new BusinessException(FundingErrorCode.GOAL_QUANTITY_EXCEEDED);
                }

                FundingParticipation participation = FundingParticipation.create(
                        c.getId(), userId, request.getAmount(),
                        quantity, request.getSeatGradeId(), request.getItemOptionId(), orderId, reservationId
                );

                participationRepository.save(participation);
                c.addParticipation(request.getAmount(), quantity);
                campaignCacheService.incrementProgress(c.getId(), request.getAmount(), quantity);

                eventPublisher.publish(
                        new FundingParticipatedEvent(
                                c.getId(), participation.getId(), orderId,
                                userId, request.getAmount(), quantity, c.getFundingType().name()
                        ),
                        EventMetadata.of("FundingCampaign", String.valueOf(c.getId()))
                );

                return ParticipationResponse.from(participation);
            });
        } catch (RuntimeException e) {
            try {
                stockClient.cancelReservation(reservationId);
            } catch (Exception cancelError) {
                log.error("Failed to cancel stock reservation after participation rollback: reservationId={}",
                        reservationId, cancelError);
            }
            throw e;
        }
    }
}
