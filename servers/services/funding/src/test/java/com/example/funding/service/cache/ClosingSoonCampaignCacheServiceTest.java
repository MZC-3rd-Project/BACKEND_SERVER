package com.example.funding.service.cache;

import com.example.core.pagination.CursorResponse;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingStatus;
import com.example.funding.entity.FundingType;
import com.example.funding.repository.FundingCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClosingSoonCampaignCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private FundingCampaignRepository campaignRepository;

    private ClosingSoonCampaignCacheService cacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new ClosingSoonCampaignCacheService(redisTemplate, campaignRepository);
    }

    @Test
    void getClosingSoonCampaigns_returnsCachedPayloadWhenPresent() {
        when(valueOperations.get(ClosingSoonCampaignCacheService.CACHE_KEY)).thenReturn(
                ClosingSoonCampaignCachePayload.builder()
                        .refreshedAt(LocalDateTime.now())
                        .items(List.of(ClosingSoonCampaignSnapshot.builder()
                                .id(101L)
                                .itemId(201L)
                                .title("마감 임박 펀딩")
                                .fundingType(FundingType.AMOUNT_BASED.name())
                                .goalAmount(100_000L)
                                .currentAmount(55_000L)
                                .status(FundingStatus.ACTIVE.name())
                                .startAt(LocalDateTime.now().minusDays(1))
                                .endAt(LocalDateTime.now().plusHours(4))
                                .build()))
                        .build()
        );

        CursorResponse<CampaignResponse> response = cacheService.getClosingSoonCampaigns(1);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getTitle()).isEqualTo("마감 임박 펀딩");
        verify(campaignRepository, never()).findClosingSoonActiveCampaigns(any(), any(), any(Pageable.class));
    }

    @Test
    void getClosingSoonCampaigns_refreshesCacheWhenMissing() {
        when(valueOperations.get(ClosingSoonCampaignCacheService.CACHE_KEY)).thenReturn(null);
        when(campaignRepository.findClosingSoonActiveCampaigns(eq(FundingStatus.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(FundingCampaign.create(
                        2001L,
                        31L,
                        "응원 타월 마감 임박",
                        "메인 슬라이드 노출용",
                        "돈모아 랩",
                        "GOODS",
                        3001L,
                        FundingType.AMOUNT_BASED,
                        300_000L,
                        100,
                        18_000L,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusHours(8)
                )));

        CursorResponse<CampaignResponse> response = cacheService.getClosingSoonCampaigns(5);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getTitle()).isEqualTo("응원 타월 마감 임박");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(campaignRepository).findClosingSoonActiveCampaigns(eq(FundingStatus.ACTIVE), any(LocalDateTime.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        verify(valueOperations).set(eq(ClosingSoonCampaignCacheService.CACHE_KEY),
                any(ClosingSoonCampaignCachePayload.class),
                eq(Duration.ofMinutes(10)));
    }

    @Test
    void getClosingSoonCampaigns_evictsBrokenCacheAndFallsBackToDatabase() {
        when(valueOperations.get(ClosingSoonCampaignCacheService.CACHE_KEY))
                .thenThrow(new SerializationException("broken payload"));
        when(campaignRepository.findClosingSoonActiveCampaigns(eq(FundingStatus.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(FundingCampaign.create(
                        3001L,
                        41L,
                        "역직렬화 복구 테스트",
                        "캐시가 깨져도 DB fallback",
                        "돈모아 랩",
                        "GOODS",
                        4001L,
                        FundingType.AMOUNT_BASED,
                        500_000L,
                        150,
                        25_000L,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusHours(6)
                )));

        CursorResponse<CampaignResponse> response = cacheService.getClosingSoonCampaigns(5);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getTitle()).isEqualTo("역직렬화 복구 테스트");
        verify(redisTemplate).delete(ClosingSoonCampaignCacheService.CACHE_KEY);
        verify(valueOperations).set(eq(ClosingSoonCampaignCacheService.CACHE_KEY),
                any(ClosingSoonCampaignCachePayload.class),
                eq(Duration.ofMinutes(10)));
    }
}
