package com.example.product.dto.performance.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CastMemberRequest {

    @NotBlank(message = "출연진 이름은 필수입니다")
    private String name;

    private String role;

    private String profileImageUrl;
}
