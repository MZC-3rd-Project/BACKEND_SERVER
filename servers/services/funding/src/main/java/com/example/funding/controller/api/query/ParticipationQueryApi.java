package com.example.funding.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.funding.dto.participation.response.ParticipationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Participation Query", description = "펀딩 참여 조회 API (읽기)")
public interface ParticipationQueryApi {

    @Operation(summary = "내 펀딩 참여 내역 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/participations/me")
    ApiResponse<CursorResponse<ParticipationResponse>> findMyParticipations(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "캠페인별 참여 내역 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{campaignId}/participations")
    ApiResponse<List<ParticipationResponse>> findByCampaignId(@PathVariable Long campaignId);
}
