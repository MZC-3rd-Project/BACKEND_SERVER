package com.example.store.service.test;

import com.example.store.entity.ImageType;
import com.example.store.service.query.StoreImageSelectionPolicy;
import com.example.store.service.query.view.StoreImageView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StoreImageSelectionPolicy 단위 테스트")
class StoreImageSelectionPolicyTest {

    private final StoreImageSelectionPolicy storeImageSelectionPolicy = new StoreImageSelectionPolicy();

    @Test
    @DisplayName("THUMBNAIL 타입 이미지를 우선 썸네일로 선택한다")
    void selectThumbnail_prefersThumbnailType() {
        LocalDateTime now = LocalDateTime.now();
        List<StoreImageView> images = List.of(
            new StoreImageView(12L, 1L, 20L, ImageType.GALLERY, 0, now),
            new StoreImageView(11L, 1L, 10L, ImageType.THUMBNAIL, 1, now.minusMinutes(1))
        );

        StoreImageView thumbnail = storeImageSelectionPolicy.selectThumbnail(images);

        assertThat(thumbnail.imageId()).isEqualTo(11L);
        assertThat(thumbnail.storeId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("썸네일이 없으면 정렬 기준 첫 이미지를 선택하고 gallery에서는 제외한다")
    void selectGallery_excludesSelectedThumbnail() {
        LocalDateTime now = LocalDateTime.now();
        List<StoreImageView> images = List.of(
            new StoreImageView(22L, 1L, 21L, ImageType.GALLERY, 2, now),
            new StoreImageView(21L, 1L, 20L, ImageType.GALLERY, 0, now.minusMinutes(1)),
            new StoreImageView(23L, 1L, 22L, ImageType.GALLERY, 1, now.minusMinutes(2))
        );

        StoreImageView thumbnail = storeImageSelectionPolicy.selectThumbnail(images);
        List<StoreImageView> gallery = storeImageSelectionPolicy.selectGallery(images, thumbnail);

        assertThat(thumbnail.imageId()).isEqualTo(21L);
        assertThat(gallery).extracting(StoreImageView::imageId).containsExactly(23L, 22L);
    }
}
