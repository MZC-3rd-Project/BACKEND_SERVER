package com.example.funding.service.query;

import com.example.funding.dto.campaign.response.FundingRewardOptionResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingType;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignQueryServiceTest {

    @Mock
    private FundingCampaignRepository campaignRepository;

    @Mock
    private FundingStatusHistoryRepository statusHistoryRepository;

    @Mock
    private FundingRewardOptionResolver fundingRewardOptionResolver;

    private CampaignQueryService campaignQueryService;

    @BeforeEach
    void setUp() {
        campaignQueryService = new CampaignQueryService(
                campaignRepository,
                statusHistoryRepository,
                fundingRewardOptionResolver
        );
    }

    @Test
    void findById_includesRewardOptionsFromResolver() {
        FundingCampaign campaign = FundingCampaign.create(
                2001L,
                31L,
                "무선 포터블 스피커 펀딩",
                "야외 활동용 초경량 스피커",
                "도모아랩",
                "오디오",
                3001L,
                FundingType.AMOUNT_BASED,
                1_000_000L,
                100,
                39_000L,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7)
        );

        when(campaignRepository.findById(1001L)).thenReturn(Optional.of(campaign));
        when(fundingRewardOptionResolver.resolveByItemId(2001L)).thenReturn(List.of(
                FundingRewardOptionResponse.builder()
                        .id(1L)
                        .title("얼리버드")
                        .price(39_000L)
                        .shippingText("4월 말 순차배송")
                        .itemOptionId(501L)
                        .build()
        ));

        var response = campaignQueryService.findById(1001L);

        assertThat(response.getItemId()).isEqualTo(2001L);
        assertThat(response.getRewardOptions()).hasSize(1);
        assertThat(response.getRewardOptions().getFirst().getItemOptionId()).isEqualTo(501L);
        assertThat(response.getRewardOptions().getFirst().getTitle()).isEqualTo("얼리버드");
        verify(fundingRewardOptionResolver).resolveByItemId(2001L);
    }
}
