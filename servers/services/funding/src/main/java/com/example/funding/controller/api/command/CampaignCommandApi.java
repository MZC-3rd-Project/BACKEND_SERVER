package com.example.funding.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.funding.dto.campaign.request.CampaignCreateRequest;
import com.example.funding.dto.campaign.request.CampaignUpdateRequest;
import com.example.funding.dto.campaign.response.CampaignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Campaign Command", description = "펀딩 캠페인 관리 API (쓰기)")
public interface CampaignCommandApi {

    @Operation(summary = "펀딩 캠페인 생성")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 캠페인")
    })
    @PostMapping
    ApiResponse<CampaignResponse> create(
            @Valid @RequestBody CampaignCreateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "펀딩 캠페인 수정")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "캠페인 없음")
    })
    @PutMapping("/{campaignId}")
    ApiResponse<CampaignResponse> update(
            @PathVariable Long campaignId,
            @Valid @RequestBody CampaignUpdateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);

    @Operation(summary = "펀딩 캠페인 취소")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "캠페인 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "취소 불가 상태")
    })
    @PostMapping("/{campaignId}/cancel")
    ApiResponse<Void> cancel(
            @PathVariable Long campaignId,
            @RequestParam(required = false) String reason,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long sellerId);
}
