package com.example.product.dto.image;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemImageRequest {

    @NotBlank(message = "이미지 URL은 필수입니다")
    private String imageUrl;

    private int sortOrder;
    private boolean isThumbnail;
}
