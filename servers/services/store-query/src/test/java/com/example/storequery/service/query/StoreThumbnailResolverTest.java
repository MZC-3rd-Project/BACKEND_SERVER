package com.example.storequery.service.query;

import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadModel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreThumbnailResolverTest {

    private final StoreQueryMediaUrlNormalizer mediaUrlNormalizer =
        new StoreQueryMediaUrlNormalizer("https://d179i4pv5hzdkg.cloudfront.net");
    private final StoreThumbnailResolver storeThumbnailResolver = new StoreThumbnailResolver(mediaUrlNormalizer);

    @Test
    void resolve_usesSummarySourceThumbnailWhenPresent() {
        StoreSummarySource source = new StoreSummarySource() {
            @Override
            public Long storeId() { return 1L; }
            @Override
            public Long userId() { return 100L; }
            @Override
            public String storeName() { return "MZC Store"; }
            @Override
            public StoreQueryStatus status() { return StoreQueryStatus.ACTIVE; }
            @Override
            public String description() { return "desc"; }
            @Override
            public String primaryContactValue() { return "010"; }
            @Override
            public String defaultAddress() { return "Seoul"; }
            @Override
            public String ownerNickname() { return "owner"; }
            @Override
            public Long thumbnailMediaId() { return 10L; }
            @Override
            public String thumbnailUrl() { return "https://thumb"; }
            @Override
            public Integer thumbnailSortOrder() { return 0; }
        };

        var thumbnail = storeThumbnailResolver.resolve(source);

        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.mediaId()).isEqualTo(10L);
        assertThat(thumbnail.imageType()).isEqualTo(StoreQueryImageType.THUMBNAIL);
        assertThat(thumbnail.mediaUrl()).isEqualTo("https://thumb");
    }

    @Test
    void resolve_forDetailFallsBackToThumbnailImageWhenModelThumbnailMissing() {
        LocalDateTime now = LocalDateTime.now();
        StoreReadModel model = StoreReadModel.of(
            1L,
            100L,
            "MZC Store",
            "owner",
            "https://owner",
            StoreQueryStatus.ACTIVE,
            "desc",
            "Seoul",
            null,
            "010",
            null,
            null,
            null,
            null,
            0,
            0,
            now,
            "search",
            now.minusDays(1),
            now.minusHours(1),
            now
        );
        List<StoreReadImage> images = List.of(
            StoreReadImage.of(1L, StoreQueryImageType.GALLERY, 11L, "https://gallery", 1, now, now),
            StoreReadImage.of(
                1L,
                StoreQueryImageType.THUMBNAIL,
                12L,
                "https://team2-donmoa-media-raw.s3.ap-northeast-2.amazonaws.com/team2-donmoa-media/raw/2026/03/18/thumb.png",
                0,
                now,
                now
            )
        );

        var thumbnail = storeThumbnailResolver.resolve(model, images);

        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.mediaId()).isEqualTo(12L);
        assertThat(thumbnail.mediaUrl())
            .isEqualTo("https://d179i4pv5hzdkg.cloudfront.net/team2-donmoa-media/raw/2026/03/18/thumb.png");
    }
}
