package com.example.media.dto.command.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UploadConfirmRequest {

    @NotNull
    private Long mediaId;

    @NotBlank
    private String uploadToken;

    private String ownerType;

    private Long ownerId;

    private String usageType;

    @Min(0)
    private Integer sortOrder;
}
