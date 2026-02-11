package com.example.funding.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.funding.controller.api.query.ParticipationQueryApi;
import com.example.funding.dto.participation.response.ParticipationResponse;
import com.example.funding.service.query.ParticipationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class ParticipationQueryController implements ParticipationQueryApi {

    private final ParticipationQueryService participationQueryService;

    @Override
    public ApiResponse<CursorResponse<ParticipationResponse>> findMyParticipations(
            Long userId, String cursor, int size) {
        return ApiResponse.success(participationQueryService.findByUserId(userId, cursor, size));
    }

    @Override
    public ApiResponse<List<ParticipationResponse>> findByCampaignId(Long campaignId) {
        return ApiResponse.success(participationQueryService.findByCampaignId(campaignId));
    }
}
