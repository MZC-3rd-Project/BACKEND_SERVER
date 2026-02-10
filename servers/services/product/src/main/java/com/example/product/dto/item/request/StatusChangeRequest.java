package com.example.product.dto.item.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StatusChangeRequest {

    @NotBlank(message = "변경할 상태는 필수입니다")
    private String status;

    private String reason;
}
