package com.example.product.dto.item.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ItemDetailSectionRequest {

    @Size(max = 100)
    private String title;

    @Size(max = 5000)
    private String description;

    @Size(max = 1000)
    private String imageUrl;

    private List<@Size(max = 200) String> highlights;
}
