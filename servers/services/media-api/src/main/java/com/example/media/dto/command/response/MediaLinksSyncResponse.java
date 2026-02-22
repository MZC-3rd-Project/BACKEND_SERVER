package com.example.media.dto.command.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MediaLinksSyncResponse {

    private String ownerType;
    private Long ownerId;
    private List<MediaUsageSetResponse> sets;
}
