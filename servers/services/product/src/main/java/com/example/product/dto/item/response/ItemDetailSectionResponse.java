package com.example.product.dto.item.response;

import com.example.product.entity.item.ItemDetailSection;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ItemDetailSectionResponse {

    private String title;
    private String description;
    private String imageUrl;
    private List<String> highlights;

    public static ItemDetailSectionResponse from(ItemDetailSection detailSection) {
        return ItemDetailSectionResponse.builder()
                .title(detailSection.getTitle())
                .description(detailSection.getDescription())
                .imageUrl(detailSection.getImageUrl())
                .highlights(detailSection.getHighlights())
                .build();
    }
}
