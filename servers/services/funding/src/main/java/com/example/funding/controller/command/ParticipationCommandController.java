package com.example.funding.controller.command;

import com.example.api.response.ApiResponse;
import com.example.core.exception.BusinessException;
import com.example.funding.controller.api.command.ParticipationCommandApi;
import com.example.funding.dto.participation.request.ParticipateRequest;
import com.example.funding.dto.participation.response.ParticipationResponse;
import com.example.funding.exception.FundingErrorCode;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
public class ParticipationCommandController implements ParticipationCommandApi {

    @Override
    public ApiResponse<ParticipationResponse> participate(Long campaignId,
                                                           ParticipateRequest request, Long userId) {
        throw new BusinessException(FundingErrorCode.PARTICIPATION_CHECKOUT_MOVED);
    }
}
