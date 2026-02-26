package com.example.media.dto.command.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MediaUsageSetResponse {

    private String usageType;
    private List<Long> mediaIds;
}
