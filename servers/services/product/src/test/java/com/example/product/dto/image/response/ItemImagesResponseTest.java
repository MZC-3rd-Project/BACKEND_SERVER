package com.example.product.dto.image.response;

import com.example.product.entity.image.ItemImage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemImagesResponseTest {

    @Test
    void from_withPreferredThumbnailMediaId_prioritizesPreferredImage() {
        ItemImage flaggedThumbnail = createImage(1L, 10L, 100L, 0, true);
        ItemImage preferred = createImage(2L, 10L, 200L, 1, false);

        ItemImagesResponse response = ItemImagesResponse.from(List.of(flaggedThumbnail, preferred), 200L);

        assertThat(response.getThumbnail()).isNotNull();
        assertThat(response.getThumbnail().getMediaId()).isEqualTo(200L);
        assertThat(response.getGallery()).extracting(ItemImageResponse::getMediaId)
                .containsExactly(100L);
    }

    @Test
    void from_withoutPreferredThumbnail_usesFlaggedThumbnail() {
        ItemImage thumbnail = createImage(1L, 10L, 100L, 0, true);
        ItemImage gallery = createImage(2L, 10L, 200L, 1, false);

        ItemImagesResponse response = ItemImagesResponse.from(List.of(thumbnail, gallery), null);

        assertThat(response.getThumbnail()).isNotNull();
        assertThat(response.getThumbnail().getMediaId()).isEqualTo(100L);
        assertThat(response.getGallery()).extracting(ItemImageResponse::getMediaId)
                .containsExactly(200L);
    }

    @Test
    void from_whenNoThumbnailFlag_fallsBackToFirstSortedImage() {
        ItemImage later = createImage(2L, 10L, 200L, 1, false);
        ItemImage first = createImage(1L, 10L, 100L, 0, false);

        ItemImagesResponse response = ItemImagesResponse.from(List.of(later, first), null);

        assertThat(response.getThumbnail()).isNotNull();
        assertThat(response.getThumbnail().getMediaId()).isEqualTo(100L);
        assertThat(response.getGallery()).extracting(ItemImageResponse::getMediaId)
                .containsExactly(200L);
    }

    private ItemImage createImage(Long id, Long itemId, Long mediaId, int sortOrder, boolean thumbnail) {
        ItemImage image = ItemImage.create(itemId, mediaId, sortOrder, thumbnail);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }
}
