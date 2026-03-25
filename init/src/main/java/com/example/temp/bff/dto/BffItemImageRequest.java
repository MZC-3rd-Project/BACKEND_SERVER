package com.example.gateway.bff.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BffItemImageRequest {

    private Long mediaId;
    private Integer sortOrder;
    private Boolean isThumbnail;
}
