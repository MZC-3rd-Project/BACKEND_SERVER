package com.example.funding.service.command;

import com.example.core.exception.BusinessException;
import com.example.clients.stock.facade.StockClientFacade;
import com.example.funding.dto.campaign.request.CampaignCreateRequest;
import com.example.funding.dto.campaign.request.CampaignUpdateRequest;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.entity.FundingStatus;
import com.example.funding.entity.FundingStatusHistory;
import com.example.funding.entity.FundingType;
import com.example.funding.event.FundingCreatedEvent;
import com.example.funding.event.FundingFailedEvent;
import com.example.funding.event.FundingRefundedEvent;
import com.example.funding.entity.ParticipationStatus;
import com.example.funding.exception.FundingErrorCode;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingParticipationRepository;
import com.example.funding.repository.FundingStatusHistoryRepository;
import com.example.funding.service.retry.StockCancelRetryService;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CampaignCommandService {

    private final FundingCampaignRepository campaignRepository;
    private final FundingParticipationRepository participationRepository;
    private final FundingStatusHistoryRepository statusHistoryRepository;
    private final StockClientFacade stockClient;
    private final EventPublisher eventPublisher;
    private final StockCancelRetryService stockCancelRetryService;

    public CampaignResponse create(CampaignCreateRequest request, Long sellerId) {
        if (campaignRepository.existsByItemId(request.getItemId())) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_ALREADY_EXISTS);
        }

        validatePeriod(request.getStartAt(), request.getEndAt());

        FundingType fundingType = FundingType.valueOf(request.getFundingType());

        FundingCampaign campaign = FundingCampaign.create(
                request.getItemId(),
                sellerId,
                fundingType,
                request.getGoalAmount(),
                request.getGoalQuantity(),
                request.getMinAmount(),
                request.getStartAt(),
                request.getEndAt()
        );

        campaignRepository.save(campaign);

        eventPublisher.publish(
                new FundingCreatedEvent(
                        campaign.getId(), campaign.getItemId(), sellerId,
                        fundingType.name(), campaign.getGoalAmount(),
                        campaign.getStartAt(), campaign.getEndAt()
                ),
                EventMetadata.of("FundingCampaign", String.valueOf(campaign.getId()))
        );

        return CampaignResponse.from(campaign);
    }

    public CampaignResponse update(Long campaignId, CampaignUpdateRequest request, Long sellerId) {
        FundingCampaign campaign = findCampaign(campaignId);
        campaign.validateOwnership(sellerId);

        validatePeriod(request.getStartAt(), request.getEndAt());

        campaign.update(
                request.getGoalAmount(),
                request.getGoalQuantity(),
                request.getMinAmount(),
                request.getStartAt(),
                request.getEndAt()
        );

        return CampaignResponse.from(campaign);
    }

    public void cancel(Long campaignId, String reason, Long sellerId) {
        FundingCampaign campaign = findCampaign(campaignId);
        campaign.validateOwnership(sellerId);

        List<FundingParticipation> pendingParticipations =
                participationRepository.findByCampaignIdAndStatus(campaignId, ParticipationStatus.PENDING);
        List<StockCancelTarget> cancelTargets = pendingParticipations.stream()
                .filter(participation -> participation.getReservationId() != null)
                .map(participation -> new StockCancelTarget(participation.getId(), participation.getReservationId()))
                .toList();

        for (FundingParticipation participation : pendingParticipations) {
            participation.refund();
            campaign.removeParticipation(participation.getAmount(), participation.getQuantity());

            eventPublisher.publish(
                    new FundingRefundedEvent(
                            campaign.getId(), participation.getId(), participation.getOrderId(),
                            participation.getUserId(), participation.getAmount()
                    ),
                    EventMetadata.of("FundingCampaign", String.valueOf(campaign.getId()))
            );
        }

        FundingStatus previousStatus = campaign.getStatus();
        campaign.changeStatus(FundingStatus.CANCELLED);

        statusHistoryRepository.save(
                FundingStatusHistory.create(campaignId, previousStatus, FundingStatus.CANCELLED, reason)
        );

        eventPublisher.publish(
                new FundingFailedEvent(
                        campaign.getId(), campaign.getItemId(), campaign.getSellerId(),
                        campaign.getFundingType().name(), campaign.getGoalAmount(),
                        campaign.getCurrentAmount(), campaign.getCurrentQuantity()
                ),
                EventMetadata.of("FundingCampaign", String.valueOf(campaign.getId()))
        );

        registerAfterCommitStockCancel(cancelTargets);
    }

    private FundingCampaign findCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));
    }

    private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (endAt.isBefore(startAt) || endAt.isEqual(startAt)) {
            throw new BusinessException(FundingErrorCode.INVALID_CAMPAIGN_PERIOD);
        }
    }

    private void registerAfterCommitStockCancel(List<StockCancelTarget> cancelTargets) {
        if (cancelTargets.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cancelReservations(cancelTargets);
                }
            });
            return;
        }

        cancelReservations(cancelTargets);
    }

    private void cancelReservations(List<StockCancelTarget> cancelTargets) {
        for (StockCancelTarget cancelTarget : cancelTargets) {
            try {
                stockClient.cancelReservation(cancelTarget.reservationId());
            } catch (Exception e) {
                log.error("Campaign cancel committed but stock reservation cancel failed: participationId={}, reservationId={}",
                        cancelTarget.participationId(), cancelTarget.reservationId(), e);
                try {
                    stockCancelRetryService.enqueue(
                            cancelTarget.participationId(),
                            cancelTarget.reservationId(),
                            e.getMessage()
                    );
                } catch (Exception enqueueError) {
                    log.error("Failed to enqueue stock cancel retry after campaign cancel: participationId={}, reservationId={}",
                            cancelTarget.participationId(), cancelTarget.reservationId(), enqueueError);
                }
            }
        }
    }

    private record StockCancelTarget(Long participationId, Long reservationId) {
    }
}
