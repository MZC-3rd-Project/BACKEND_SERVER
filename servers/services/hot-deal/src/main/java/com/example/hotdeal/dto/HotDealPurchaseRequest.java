package com.example.hotdeal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealPurchaseRequest {

    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private Integer quantity;

    /**
     * 대기열 입장 시 발급한 토큰.
     * 하위 호환을 위해 선택 입력으로 두고, 서버 설정에 따라 검증 강도를 조절한다.
     */
    private String token;
}
