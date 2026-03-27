package com.example.funding.service.command;

import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.funding.client.FundingOrderLookupClient;
import com.example.funding.client.FundingOrderSnapshot;
import com.example.funding.consumer.payment.PaymentEventMessage;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.entity.FundingStatus;
import com.example.funding.entity.ParticipationStatus;
import com.example.funding.event.FundingParticipatedEvent;
import com.example.funding.event.FundingRefundedEvent;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingParticipationRepository;
import com.example.funding.service.CampaignCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingParticipationSyncService {

    private static final String FUNDING_CHANNEL_TYPE = "FUNDING";

    private final FundingOrderLookupClient fundingOrderLookupClient;
    private final FundingCampaignRepository fundingCampaignRepository;
    private final FundingParticipationRepository fundingParticipationRepository;
    private final CampaignCacheService campaignCacheService;
    private final EventPublisher eventPublisher;

    @Transactional
    public void syncPaymentCompleted(PaymentEventMessage event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Skip funding payment sync. orderId is missing.");
            return;
        }

        FundingOrderSnapshot order = fundingOrderLookupClient.findOrder(event.getOrderId());
        if (order == null) {
            log.warn("Skip funding payment sync. order not found. orderId={}", event.getOrderId());
            return;
        }

        Long userId = order.userId() != null ? order.userId() : event.getUserId();
        if (userId == null) {
            log.warn("Skip funding payment sync. userId is missing. orderId={}", event.getOrderId());
            return;
        }

        Map<Long, FundingLineAggregate> fundingLineAggregates = aggregateFundingLineItems(order.lineItems());
        if (fundingLineAggregates.isEmpty()) {
            log.debug("Skip funding payment sync. no FUNDING line items. orderId={}", event.getOrderId());
            return;
        }

        for (FundingLineAggregate aggregate : fundingLineAggregates.values()) {
            if (fundingParticipationRepository.findByOrderIdAndCampaignId(order.orderId(), aggregate.campaignId()).isPresent()) {
                log.info("Funding participation already exists. orderId={}, campaignId={}",
                        order.orderId(), aggregate.campaignId());
                continue;
            }

            FundingCampaign campaign = fundingCampaignRepository.findByIdWithLock(aggregate.campaignId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Funding campaign not found for paid order. campaignId="
                                    + aggregate.campaignId() + ", orderId=" + order.orderId()
                    ));

            if (campaign.getStatus() != FundingStatus.ACTIVE) {
                log.warn("Skip funding participation sync. campaign is not active. orderId={}, campaignId={}, status={}",
                        order.orderId(), campaign.getId(), campaign.getStatus());
                continue;
            }

            campaign.addParticipation(aggregate.amount(), aggregate.quantity());
            campaignCacheService.cacheProgress(campaign);

            FundingParticipation participation = fundingParticipationRepository.save(
                    FundingParticipation.createConfirmed(
                            campaign.getId(),
                            userId,
                            aggregate.amount(),
                            aggregate.quantity(),
                            null,
                            null,
                            order.orderId(),
                            event.getPaymentId()
                    )
            );

            eventPublisher.publish(
                    new FundingParticipatedEvent(
                            campaign.getId(),
                            campaign.getItemId(),
                            campaign.getSellerId(),
                            participation.getId(),
                            participation.getOrderId(),
                            participation.getUserId(),
                            participation.getAmount(),
                            participation.getQuantity(),
                            campaign.getFundingType().name()
                    ),
                    EventMetadata.of("FundingParticipation", String.valueOf(participation.getId()))
            );

            log.info("Funding participation synced from payment completion. paymentId={}, orderId={}, campaignId={}, participationId={}",
                    event.getPaymentId(), order.orderId(), campaign.getId(), participation.getId());
        }
    }

    @Transactional
    public void syncPaymentRefunded(PaymentEventMessage event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Skip funding refund sync. orderId is missing.");
            return;
        }

        List<FundingParticipation> participations = fundingParticipationRepository.findByOrderId(event.getOrderId());
        if (participations.isEmpty()) {
            log.debug("Skip funding refund sync. no funding participations. orderId={}", event.getOrderId());
            return;
        }

        for (FundingParticipation participation : participations) {
            if (participation.getStatus() == ParticipationStatus.REFUNDED) {
                continue;
            }

            FundingCampaign campaign = fundingCampaignRepository.findByIdWithLock(participation.getCampaignId()).orElse(null);
            Long itemId = null;
            Long sellerId = null;
            String fundingType = null;

            if (campaign != null) {
                campaign.removeParticipation(participation.getAmount(), participation.getQuantity());
                campaignCacheService.cacheProgress(campaign);
                itemId = campaign.getItemId();
                sellerId = campaign.getSellerId();
                fundingType = campaign.getFundingType().name();
            } else {
                log.warn("Funding campaign missing while refunding participation. orderId={}, campaignId={}, participationId={}",
                        event.getOrderId(), participation.getCampaignId(), participation.getId());
            }

            participation.refund();

            eventPublisher.publish(
                    new FundingRefundedEvent(
                            participation.getCampaignId(),
                            itemId,
                            sellerId,
                            participation.getId(),
                            participation.getOrderId(),
                            participation.getUserId(),
                            participation.getAmount(),
                            participation.getQuantity(),
                            fundingType
                    ),
                    EventMetadata.of("FundingParticipation", String.valueOf(participation.getId()))
            );

            log.info("Funding participation refunded from payment refund. paymentId={}, orderId={}, campaignId={}, participationId={}",
                    event.getPaymentId(), participation.getOrderId(), participation.getCampaignId(), participation.getId());
        }
    }

    private Map<Long, FundingLineAggregate> aggregateFundingLineItems(List<FundingOrderSnapshot.LineItem> lineItems) {
        Map<Long, FundingLineAggregate> aggregates = new LinkedHashMap<>();
        if (lineItems == null || lineItems.isEmpty()) {
            return aggregates;
        }

        for (FundingOrderSnapshot.LineItem lineItem : lineItems) {
            if (lineItem == null
                    || !FUNDING_CHANNEL_TYPE.equals(normalizeChannelType(lineItem.channelType()))
                    || lineItem.channelRefId() == null
                    || lineItem.channelRefId() <= 0L
                    || lineItem.quantity() == null
                    || lineItem.quantity() <= 0
                    || lineItem.lineAmount() == null
                    || lineItem.lineAmount() <= 0L) {
                continue;
            }

            aggregates.merge(
                    lineItem.channelRefId(),
                    new FundingLineAggregate(lineItem.channelRefId(), lineItem.quantity(), lineItem.lineAmount()),
                    FundingLineAggregate::merge
            );
        }

        return aggregates;
    }

    private String normalizeChannelType(String channelType) {
        return channelType == null ? null : channelType.trim().toUpperCase(Locale.ROOT);
    }

    private record FundingLineAggregate(Long campaignId, Integer quantity, Long amount) {

        private FundingLineAggregate merge(FundingLineAggregate other) {
            return new FundingLineAggregate(
                    campaignId,
                    quantity + other.quantity,
                    amount + other.amount
            );
        }
    }
}
