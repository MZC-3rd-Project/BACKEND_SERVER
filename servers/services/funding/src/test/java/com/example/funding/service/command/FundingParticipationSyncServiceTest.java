package com.example.funding.service.command;

import com.example.event.DomainEvent;
import com.example.event.EventPublisher;
import com.example.funding.client.FundingOrderLookupClient;
import com.example.funding.client.FundingOrderSnapshot;
import com.example.funding.consumer.payment.PaymentEventMessage;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingParticipation;
import com.example.funding.entity.ParticipationStatus;
import com.example.funding.event.FundingParticipatedEvent;
import com.example.funding.event.FundingRefundedEvent;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingParticipationRepository;
import com.example.funding.service.CampaignCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingParticipationSyncServiceTest {

    @Mock
    private FundingOrderLookupClient fundingOrderLookupClient;

    @Mock
    private FundingCampaignRepository fundingCampaignRepository;

    @Mock
    private FundingParticipationRepository fundingParticipationRepository;

    @Mock
    private CampaignCacheService campaignCacheService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private FundingParticipationSyncService fundingParticipationSyncService;

    @Test
    void syncPaymentCompleted_createsAggregatedParticipationAndPublishesFundingEvent() {
        FundingCampaign campaign = campaign(4401L, 930002L, 4001L);
        FundingOrderSnapshot orderSnapshot = new FundingOrderSnapshot(
                12345L,
                1001L,
                "PAID",
                List.of(
                        new FundingOrderSnapshot.LineItem("FUNDING", 4401L, 930002L, 1, 10000L),
                        new FundingOrderSnapshot.LineItem("FUNDING", 4401L, 930002L, 2, 20000L),
                        new FundingOrderSnapshot.LineItem("NORMAL", null, 930003L, 1, 3000L)
                )
        );

        when(fundingOrderLookupClient.findOrder(12345L)).thenReturn(orderSnapshot);
        when(fundingParticipationRepository.findByOrderIdAndCampaignId(12345L, 4401L)).thenReturn(Optional.empty());
        when(fundingCampaignRepository.findByIdWithLock(4401L)).thenReturn(Optional.of(campaign));
        when(fundingParticipationRepository.save(any(FundingParticipation.class))).thenAnswer(invocation -> {
            FundingParticipation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9001L);
            return saved;
        });

        fundingParticipationSyncService.syncPaymentCompleted(paymentEvent("evt-paid", "PAYMENT_COMPLETED", 777L, 12345L, 1001L));

        assertThat(campaign.getCurrentAmount()).isEqualTo(30000L);
        assertThat(campaign.getCurrentQuantity()).isEqualTo(3);
        verify(campaignCacheService).cacheProgress(campaign);

        ArgumentCaptor<FundingParticipation> participationCaptor = ArgumentCaptor.forClass(FundingParticipation.class);
        verify(fundingParticipationRepository).save(participationCaptor.capture());
        FundingParticipation savedParticipation = participationCaptor.getValue();
        assertThat(savedParticipation.getCampaignId()).isEqualTo(4401L);
        assertThat(savedParticipation.getUserId()).isEqualTo(1001L);
        assertThat(savedParticipation.getAmount()).isEqualTo(30000L);
        assertThat(savedParticipation.getQuantity()).isEqualTo(3);
        assertThat(savedParticipation.getOrderId()).isEqualTo(12345L);
        assertThat(savedParticipation.getPaymentId()).isEqualTo(777L);
        assertThat(savedParticipation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture(), any());
        assertThat(eventCaptor.getValue()).isInstanceOf(FundingParticipatedEvent.class);
        FundingParticipatedEvent publishedEvent = (FundingParticipatedEvent) eventCaptor.getValue();
        assertThat(publishedEvent.getCampaignId()).isEqualTo(4401L);
        assertThat(publishedEvent.getParticipationId()).isEqualTo(9001L);
        assertThat(publishedEvent.getOrderId()).isEqualTo(12345L);
        assertThat(publishedEvent.getUserId()).isEqualTo(1001L);
        assertThat(publishedEvent.getAmount()).isEqualTo(30000L);
        assertThat(publishedEvent.getQuantity()).isEqualTo(3);
    }

    @Test
    void syncPaymentCompleted_skipsWhenParticipationAlreadyExists() {
        FundingOrderSnapshot orderSnapshot = new FundingOrderSnapshot(
                12345L,
                1001L,
                "PAID",
                List.of(new FundingOrderSnapshot.LineItem("FUNDING", 4401L, 930002L, 1, 10000L))
        );

        when(fundingOrderLookupClient.findOrder(12345L)).thenReturn(orderSnapshot);
        when(fundingParticipationRepository.findByOrderIdAndCampaignId(12345L, 4401L))
                .thenReturn(Optional.of(FundingParticipation.createConfirmed(4401L, 1001L, 10000L, 1, null, null, 12345L, 777L)));

        fundingParticipationSyncService.syncPaymentCompleted(paymentEvent("evt-paid", "PAYMENT_COMPLETED", 777L, 12345L, 1001L));

        verify(fundingCampaignRepository, never()).findByIdWithLock(any());
        verify(fundingParticipationRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(DomainEvent.class), any());
    }

    @Test
    void syncPaymentRefunded_marksParticipationRefundedAndPublishesFundingRefundedEvent() {
        FundingCampaign campaign = campaign(4401L, 930002L, 4001L);
        campaign.addParticipation(30000L, 3);

        FundingParticipation participation = FundingParticipation.createConfirmed(
                4401L,
                1001L,
                30000L,
                3,
                null,
                null,
                12345L,
                777L
        );
        ReflectionTestUtils.setField(participation, "id", 9001L);

        when(fundingParticipationRepository.findByOrderId(12345L)).thenReturn(List.of(participation));
        when(fundingCampaignRepository.findByIdWithLock(4401L)).thenReturn(Optional.of(campaign));

        fundingParticipationSyncService.syncPaymentRefunded(paymentEvent("evt-refund", "PAYMENT_REFUNDED", 777L, 12345L, 1001L));

        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.REFUNDED);
        assertThat(campaign.getCurrentAmount()).isEqualTo(0L);
        assertThat(campaign.getCurrentQuantity()).isEqualTo(0);
        verify(campaignCacheService).cacheProgress(campaign);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture(), any());
        assertThat(eventCaptor.getValue()).isInstanceOf(FundingRefundedEvent.class);
        FundingRefundedEvent publishedEvent = (FundingRefundedEvent) eventCaptor.getValue();
        assertThat(publishedEvent.getCampaignId()).isEqualTo(4401L);
        assertThat(publishedEvent.getParticipationId()).isEqualTo(9001L);
        assertThat(publishedEvent.getOrderId()).isEqualTo(12345L);
        assertThat(publishedEvent.getUserId()).isEqualTo(1001L);
        assertThat(publishedEvent.getAmount()).isEqualTo(30000L);
        assertThat(publishedEvent.getQuantity()).isEqualTo(3);
    }

    private FundingCampaign campaign(Long campaignId, Long itemId, Long sellerId) {
        FundingCampaign campaign = FundingCampaign.create(
                itemId,
                sellerId,
                "MZC Funding",
                "summary",
                "maker",
                "category",
                null,
                com.example.funding.entity.FundingType.AMOUNT_BASED,
                100000L,
                100,
                10000L,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        ReflectionTestUtils.setField(campaign, "id", campaignId);
        return campaign;
    }

    private PaymentEventMessage paymentEvent(String eventId, String eventType, Long paymentId, Long orderId, Long userId) {
        PaymentEventMessage event = new PaymentEventMessage();
        ReflectionTestUtils.setField(event, "eventId", eventId);
        ReflectionTestUtils.setField(event, "eventType", eventType);
        ReflectionTestUtils.setField(event, "paymentId", paymentId);
        ReflectionTestUtils.setField(event, "orderId", orderId);
        ReflectionTestUtils.setField(event, "userId", userId);
        return event;
    }
}
