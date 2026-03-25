package com.example.gateway.bff.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class BffItemCreateCommandRequest {

    @JsonAlias({"product", "goods", "performance", "item"})
    private JsonNode item;
    private List<BffItemImageRequest> images;
}
