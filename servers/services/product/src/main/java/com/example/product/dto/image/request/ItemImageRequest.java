package com.example.product.dto.image.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemImageRequest {

    @NotNull(message = "미디어 ID는 필수입니다")
    @Positive(message = "미디어 ID는 양수여야 합니다")
    private Long mediaId;

    @Min(value = 0, message = "이미지 순서는 0 이상이어야 합니다")
    private int sortOrder;

    @JsonProperty("isThumbnail")
    private boolean isThumbnail;
}
