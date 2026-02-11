package com.example.funding.controller.command;

import com.example.api.response.ApiResponse;
import com.example.funding.controller.api.command.ParticipationCommandApi;
import com.example.funding.dto.participation.request.ParticipateRequest;
import com.example.funding.dto.participation.response.ParticipationResponse;
import com.example.funding.service.command.ParticipationCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class ParticipationCommandController implements ParticipationCommandApi {

    private final ParticipationCommandService participationCommandService;

    @Override
    public ApiResponse<ParticipationResponse> participate(Long campaignId,
                                                           ParticipateRequest request, Long userId) {
        return ApiResponse.success(participationCommandService.participate(campaignId, request, userId));
    }

    @Override
    public ApiResponse<Void> refund(Long participationId, Long userId) {
        participationCommandService.refund(participationId, userId);
        return ApiResponse.success();
    }
}
