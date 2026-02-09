package com.example.product.dto.item;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StatusChangeRequest {

    @NotNull(message = "변경할 상태는 필수입니다")
    private String status;

    private String reason;
}
