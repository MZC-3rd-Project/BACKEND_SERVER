package com.example.funding.controller.query;

import com.example.api.response.ApiResponse;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.service.query.InternalCampaignQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Internal", description = "내부 서비스 간 호출용 API")
@RestController
@RequestMapping("/internal/v1/campaigns")
@RequiredArgsConstructor
public class InternalCampaignQueryController {

    private final InternalCampaignQueryService internalCampaignQueryService;

    @Operation(summary = "캠페인 단건 조회 (내부)")
    @GetMapping("/{campaignId}")
    public ApiResponse<CampaignResponse> findById(@PathVariable Long campaignId) {
        return ApiResponse.success(internalCampaignQueryService.findById(campaignId));
    }

    @Operation(summary = "상품별 캠페인 조회 (내부)")
    @GetMapping("/item/{itemId}")
    public ApiResponse<CampaignResponse> findByItemId(@PathVariable Long itemId) {
        return ApiResponse.success(internalCampaignQueryService.findByItemId(itemId));
    }

    @Operation(summary = "캠페인 다건 조회 (내부)")
    @PostMapping("/batch")
    public ApiResponse<List<CampaignResponse>> findByIds(@RequestBody List<Long> campaignIds) {
        return ApiResponse.success(internalCampaignQueryService.findByIds(campaignIds));
    }
}
