package com.example.funding.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.funding.dto.participation.request.ParticipateRequest;
import com.example.funding.dto.participation.response.ParticipationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Participation Command", description = "펀딩 참여 API (쓰기)")
public interface ParticipationCommandApi {

    @Operation(summary = "펀딩 참여")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "참여 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "캠페인 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족")
    })
    @PostMapping("/{campaignId}/participate")
    ApiResponse<ParticipationResponse> participate(
            @PathVariable Long campaignId,
            @Valid @RequestBody ParticipateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);

    @Operation(summary = "펀딩 참여 환불")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "환불 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "참여 내역 없음")
    })
    @PostMapping("/participations/{participationId}/refund")
    ApiResponse<Void> refund(
            @PathVariable Long participationId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);
}
