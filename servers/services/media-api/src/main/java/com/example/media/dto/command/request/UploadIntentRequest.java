package com.example.media.dto.command.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UploadIntentRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @Min(1)
    private long fileSize;

    private String ownerType;

    private Long ownerId;

    private String usageType;

    @Min(0)
    private Integer sortOrder;
}
