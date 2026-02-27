package com.example.media.dto.command.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MediaLinksSyncRequest {

    @NotBlank
    private String ownerType;

    @NotNull
    private Long ownerId;

    @Valid
    private List<MediaUsageSetRequest> sets;
}
