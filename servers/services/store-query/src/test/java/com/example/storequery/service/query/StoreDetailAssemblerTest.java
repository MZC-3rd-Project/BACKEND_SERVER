package com.example.storequery.service.query;

import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadItem;
import com.example.storequery.entity.StoreReadModel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreDetailAssemblerTest {

    private final StoreQueryMediaUrlNormalizer mediaUrlNormalizer =
        new StoreQueryMediaUrlNormalizer("https://d179i4pv5hzdkg.cloudfront.net");
    private final StoreDetailAssembler storeDetailAssembler =
        new StoreDetailAssembler(new StoreThumbnailResolver(mediaUrlNormalizer), mediaUrlNormalizer);

    @Test
    void toDetailResponse_buildsThumbnailGalleryAndItemSummaries() {
        LocalDateTime now = LocalDateTime.now();
        StoreReadModel model = StoreReadModel.of(
            1L,
            100L,
            "MZC Store",
            "owner",
            "https://team2-donmoa-media-raw.s3.ap-northeast-2.amazonaws.com/team2-donmoa-media/raw/2026/03/18/owner.png",
            StoreQueryStatus.ACTIVE,
            "desc",
            "Seoul",
            null,
            "010-1234-5678",
            null,
            10L,
            "https://team2-donmoa-media-raw.s3.ap-northeast-2.amazonaws.com/team2-donmoa-media/raw/2026/03/18/thumb.png",
            0,
            2,
            1,
            now,
            "search",
            now.minusDays(1),
            now.minusHours(1),
            now
        );
        List<StoreReadImage> images = List.of(
            StoreReadImage.of(
                1L,
                StoreQueryImageType.THUMBNAIL,
                10L,
                "https://team2-donmoa-media-raw.s3.ap-northeast-2.amazonaws.com/team2-donmoa-media/raw/2026/03/18/thumb.png",
                0,
                now,
                now
            ),
            StoreReadImage.of(1L, StoreQueryImageType.GALLERY, 11L, "https://gallery", 1, now, now)
        );
        List<StoreReadItem> items = List.of(
            StoreReadItem.of(
                1000L,
                1L,
                100L,
                "item1",
                1000L,
                "GOODS",
                "ON_SALE",
                21L,
                "https://team2-donmoa-media-raw.s3.ap-northeast-2.amazonaws.com/team2-donmoa-media/raw/2026/03/18/item.png",
                now,
                now
            )
        );

        var response = storeDetailAssembler.toDetailResponse(model, images, items);

        assertThat(response.storeId()).isEqualTo(1L);
        assertThat(response.images().thumbnail()).isNotNull();
        assertThat(response.images().thumbnail().mediaId()).isEqualTo(10L);
        assertThat(response.images().gallery()).hasSize(1);
        assertThat(response.images().gallery().getFirst().mediaId()).isEqualTo(11L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().itemId()).isEqualTo(1000L);
        assertThat(response.items().getFirst().thumbnailMediaId()).isEqualTo(21L);
        assertThat(response.ownerProfileImageUrl())
            .isEqualTo("https://d179i4pv5hzdkg.cloudfront.net/team2-donmoa-media/raw/2026/03/18/owner.png");
        assertThat(response.images().thumbnail().mediaUrl())
            .isEqualTo("https://d179i4pv5hzdkg.cloudfront.net/team2-donmoa-media/raw/2026/03/18/thumb.png");
        assertThat(response.items().getFirst().thumbnailUrl())
            .isEqualTo("https://d179i4pv5hzdkg.cloudfront.net/team2-donmoa-media/raw/2026/03/18/item.png");
    }
}
