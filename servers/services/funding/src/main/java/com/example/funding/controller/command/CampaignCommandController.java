package com.example.funding.controller.command;

import com.example.api.response.ApiResponse;
import com.example.funding.controller.api.command.CampaignCommandApi;
import com.example.funding.dto.campaign.request.CampaignCreateRequest;
import com.example.funding.dto.campaign.request.CampaignUpdateRequest;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.service.command.CampaignCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignCommandController implements CampaignCommandApi {

    private final CampaignCommandService campaignCommandService;

    @Override
    public ApiResponse<CampaignResponse> create(CampaignCreateRequest request, Long sellerId) {
        return ApiResponse.success(campaignCommandService.create(request, sellerId));
    }

    @Override
    public ApiResponse<CampaignResponse> update(Long campaignId, CampaignUpdateRequest request, Long sellerId) {
        return ApiResponse.success(campaignCommandService.update(campaignId, request, sellerId));
    }

    @Override
    public ApiResponse<Void> cancel(Long campaignId, String reason, Long sellerId) {
        campaignCommandService.cancel(campaignId, reason, sellerId);
        return ApiResponse.success();
    }
}
