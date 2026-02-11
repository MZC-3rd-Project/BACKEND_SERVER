package com.example.funding.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.funding.controller.api.query.CampaignQueryApi;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.dto.campaign.response.ProgressResponse;
import com.example.funding.dto.campaign.response.StatusHistoryResponse;
import com.example.funding.service.CampaignCacheService;
import com.example.funding.service.query.CampaignQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignQueryController implements CampaignQueryApi {

    private final CampaignQueryService campaignQueryService;
    private final CampaignCacheService campaignCacheService;

    @Override
    public ApiResponse<CampaignResponse> findById(Long campaignId) {
        return ApiResponse.success(campaignQueryService.findById(campaignId));
    }

    @Override
    public ApiResponse<CampaignResponse> findByItemId(Long itemId) {
        return ApiResponse.success(campaignQueryService.findByItemId(itemId));
    }

    @Override
    public ApiResponse<CursorResponse<CampaignResponse>> findList(String cursor, int size, String status) {
        return ApiResponse.success(campaignQueryService.findList(cursor, size, status));
    }

    @Override
    public ApiResponse<ProgressResponse> getProgress(Long campaignId) {
        return ApiResponse.success(campaignCacheService.getProgress(campaignId));
    }

    @Override
    public ApiResponse<List<StatusHistoryResponse>> findStatusHistory(Long campaignId) {
        return ApiResponse.success(campaignQueryService.findStatusHistory(campaignId));
    }
}
