package com.example.hotdeal.controller;

import com.example.api.response.ApiResponse;
import com.example.hotdeal.dto.HotDealDetailResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.service.HotDealCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    public ApiResponse<HotDealDetailResponse> createHotDeal(@Valid @RequestBody CreateHotDealRequest request) {
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
        @NotNull(message = "상품 ID는 필수입니다")
        @Positive(message = "상품 ID는 양수여야 합니다")
        private Long itemId;

        @NotBlank(message = "핫딜 제목은 필수입니다")
        private String title;

        @NotNull(message = "원가는 필수입니다")
        @Min(value = 0, message = "원가는 0 이상이어야 합니다")
        private Long originalPrice;

        @NotNull(message = "할인율은 필수입니다")
        @Min(value = 1, message = "할인율은 1% 이상이어야 합니다")
        @Max(value = 90, message = "할인율은 90% 이하여야 합니다")
        private Integer discountRate;

        @NotNull(message = "최대 수량은 필수입니다")
        @Min(value = 1, message = "최대 수량은 1 이상이어야 합니다")
        private Integer maxQuantity;

        @Min(value = 1, message = "1인당 최대 구매 수량은 1 이상이어야 합니다")
        private Integer maxPerUser;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
    }
}
