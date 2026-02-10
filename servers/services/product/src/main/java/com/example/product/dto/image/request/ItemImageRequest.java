package com.example.product.dto.image.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemImageRequest {

    @NotBlank(message = "이미지 URL은 필수입니다")
    private String imageUrl;

    private int sortOrder;

    @JsonProperty("isThumbnail")
    private boolean isThumbnail;
}
