package com.example.funding.service.query;

import com.example.core.exception.BusinessException;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.exception.FundingErrorCode;
import com.example.funding.repository.FundingCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalCampaignQueryService {

    private final FundingCampaignRepository campaignRepository;

    public CampaignResponse findById(Long campaignId) {
        FundingCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));
        return CampaignResponse.from(campaign);
    }

    public CampaignResponse findByItemId(Long itemId) {
        FundingCampaign campaign = campaignRepository.findByItemId(itemId)
                .orElseThrow(() -> new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND));
        return CampaignResponse.from(campaign);
    }

    public List<CampaignResponse> findByIds(List<Long> campaignIds) {
        return campaignRepository.findAllById(campaignIds).stream()
                .map(CampaignResponse::from)
                .toList();
    }
}
