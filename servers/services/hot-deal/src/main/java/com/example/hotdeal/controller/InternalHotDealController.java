package com.example.hotdeal.controller;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.dto.HotDealDetailResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.service.HotDealCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Internal", description = "내부 서비스 간 호출 / 테스트용 API")
@RestController
@RequestMapping("/internal/v1/hot-deals")
@RequiredArgsConstructor
public class InternalHotDealController {

    private final HotDealCommandService hotDealCommandService;

    @Operation(summary = "핫딜 수동 생성 (내부/테스트용)")
    @PostMapping
    public ApiResponse<HotDealDetailResponse> createHotDeal(@RequestBody CreateHotDealRequest request) {
        HotDeal hotDeal = hotDealCommandService.createAndActivate(
                request.getItemId(),
                request.getTitle(),
                request.getOriginalPrice(),
                request.getDiscountRate(),
                request.getMaxQuantity(),
                request.getMaxPerUser() != null ? request.getMaxPerUser() : 1,
                request.getStartAt() != null ? request.getStartAt() : LocalDateTime.now(),
                request.getEndAt() != null ? request.getEndAt() : LocalDateTime.now().plusHours(1),
                "수동 생성");
        return ApiResponse.success(HotDealDetailResponse.from(hotDeal));
    }

    @Getter
    @NoArgsConstructor
    public static class CreateHotDealRequest {
        private Long itemId;
        private String title;
        private Long originalPrice;
        private Integer discountRate;
        private Integer maxQuantity;
        private Integer maxPerUser;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
    }
}
