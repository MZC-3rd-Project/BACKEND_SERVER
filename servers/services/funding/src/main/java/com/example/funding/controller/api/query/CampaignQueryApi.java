package com.example.funding.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.dto.campaign.response.StatusHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Campaign Query", description = "펀딩 캠페인 조회 API (읽기)")
public interface CampaignQueryApi {

    @Operation(summary = "펀딩 캠페인 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "캠페인 없음")
    })
    @GetMapping("/{campaignId}")
    ApiResponse<CampaignResponse> findById(@PathVariable Long campaignId);

    @Operation(summary = "상품별 펀딩 캠페인 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "캠페인 없음")
    })
    @GetMapping("/item/{itemId}")
    ApiResponse<CampaignResponse> findByItemId(@PathVariable Long itemId);

    @Operation(summary = "펀딩 캠페인 목록 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    ApiResponse<CursorResponse<CampaignResponse>> findList(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status);

    @Operation(summary = "펀딩 캠페인 상태 이력 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "캠페인 없음")
    })
    @GetMapping("/{campaignId}/status-history")
    ApiResponse<List<StatusHistoryResponse>> findStatusHistory(@PathVariable Long campaignId);
}
