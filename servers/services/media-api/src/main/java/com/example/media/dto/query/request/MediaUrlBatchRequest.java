package com.example.media.dto.query.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MediaUrlBatchRequest {

    @NotEmpty(message = "mediaIds 는 비어 있을 수 없습니다")
    private List<@NotNull @Positive Long> mediaIds;
}
