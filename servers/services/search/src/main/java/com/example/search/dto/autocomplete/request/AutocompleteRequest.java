package com.example.search.dto.autocomplete.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AutocompleteRequest {

    @NotBlank(message = "자동완성 검색어(q)는 필수입니다.")
    private String q;

    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 20, message = "size는 20 이하여야 합니다.")
    private Integer size = 10;
}
