package com.example.search.dto.search.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SearchRequest {

    private String q;

    private String category;

    private String domainType;

    private String channel = "ALL";

    private List<String> status = new ArrayList<>();

    @Min(value = 0, message = "최소 가격은 0 이상이어야 합니다.")
    private Long minPrice;

    @Min(value = 0, message = "최대 가격은 0 이상이어야 합니다.")
    private Long maxPrice;

    private String sort = "LATEST";

    private String cursor;

    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 100, message = "size는 100 이하여야 합니다.")
    private Integer size = 20;
}
