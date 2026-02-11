package com.example.funding.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.dto.campaign.response.StatusHistoryResponse;
import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingStatus;
import com.example.funding.exception.FundingErrorCode;
import com.example.funding.repository.FundingCampaignRepository;
import com.example.funding.repository.FundingStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignQueryService {

    private final FundingCampaignRepository campaignRepository;
    private final FundingStatusHistoryRepository statusHistoryRepository;

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

    public CursorResponse<CampaignResponse> findList(String cursor, int size, String status) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<FundingCampaign> campaigns;
        if (status != null && !status.isEmpty()) {
            FundingStatus fundingStatus = FundingStatus.valueOf(status);
            campaigns = campaignRepository.findByStatusWithCursor(fundingStatus, cursorId, pageable);
        } else {
            campaigns = campaignRepository.findAllWithCursor(cursorId, pageable);
        }

        boolean hasNext = campaigns.size() > size;
        List<FundingCampaign> pageItems = hasNext ? campaigns.subList(0, size) : campaigns;

        List<CampaignResponse> content = pageItems.stream()
                .map(CampaignResponse::from)
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    public List<StatusHistoryResponse> findStatusHistory(Long campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_FOUND);
        }

        return statusHistoryRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId)
                .stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }
}
