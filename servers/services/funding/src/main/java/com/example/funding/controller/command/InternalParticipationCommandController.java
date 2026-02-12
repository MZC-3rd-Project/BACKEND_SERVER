package com.example.funding.controller.command;

import com.example.api.response.ApiResponse;
import com.example.funding.service.command.ParticipationCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal", description = "내부 서비스 간 호출용 API")
@RestController
@RequestMapping("/internal/v1/funding/participations")
@RequiredArgsConstructor
public class InternalParticipationCommandController {

    private final ParticipationCommandService participationCommandService;

    @Operation(summary = "펀딩 참여 환불 (내부)")
    @PostMapping("/{participationId}/refund")
    public ApiResponse<Void> refund(@PathVariable Long participationId,
                                     @RequestParam Long userId) {
        participationCommandService.refund(participationId, userId);
        return ApiResponse.success();
    }
}
