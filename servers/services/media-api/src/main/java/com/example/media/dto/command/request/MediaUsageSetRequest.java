package com.example.media.dto.command.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MediaUsageSetRequest {

    @NotBlank
    private String usageType;

    private List<Long> mediaIds;
}
