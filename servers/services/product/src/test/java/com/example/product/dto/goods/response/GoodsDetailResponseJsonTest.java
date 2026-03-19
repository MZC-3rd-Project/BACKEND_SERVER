package com.example.product.dto.goods.response;

import com.example.product.dto.image.response.ItemImageResponse;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoodsDetailResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialize_includesFrontendFriendlyAliases() throws Exception {
        GoodsDetailResponse response = GoodsDetailResponse.builder()
                .id(101L)
                .title("item")
                .price(12000L)
                .status("ON_SALE")
                .storeId(55L)
                .categoryId(11L)
                .categoryName("Electronics")
                .categoryPath(List.of("Root", "Electronics"))
                .images(ItemImagesResponse.builder()
                        .thumbnail(ItemImageResponse.builder()
                                .id(1L)
                                .mediaId(3001L)
                                .sortOrder(0)
                                .isThumbnail(true)
                                .build())
                        .gallery(List.of())
                        .build())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.path("id").asText()).isEqualTo("101");
        assertThat(json.path("itemId").asText()).isEqualTo("101");
        assertThat(json.path("thumbnail").path("mediaId").asText()).isEqualTo("3001");
        assertThat(json.path("category").path("id").asText()).isEqualTo("11");
        assertThat(json.path("category").path("name").asText()).isEqualTo("Electronics");
        assertThat(json.path("category").path("path")).hasSize(2);
        assertThat(json.path("storeId").asText()).isEqualTo("55");
        assertThat(json.path("status").asText()).isEqualTo("ON_SALE");
    }
}
