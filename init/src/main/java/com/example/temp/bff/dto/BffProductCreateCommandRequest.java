package com.example.gateway.bff.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class BffProductCreateCommandRequest {

    private JsonNode product;
    private List<BffItemImageRequest> images;
}
