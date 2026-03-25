package com.example.gateway.bff.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class BffProductUpdateCommandRequest {

    private JsonNode product;
    private List<BffItemImageRequest> addImages;
    private List<Long> deleteImageIds;
    private List<Long> reorderImageIds;
}
