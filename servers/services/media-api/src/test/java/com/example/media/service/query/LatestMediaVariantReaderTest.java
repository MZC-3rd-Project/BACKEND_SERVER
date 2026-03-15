package com.example.media.service.query;

import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaDerivativeStatus;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaOwnerType;
import com.example.media.entity.MediaUsageType;
import com.example.media.repository.MediaDerivativeRepository;
import com.example.media.repository.MediaLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatestMediaVariantReaderTest {

    @Mock
    private MediaLinkRepository mediaLinkRepository;

    @Mock
    private MediaDerivativeRepository mediaDerivativeRepository;

    @InjectMocks
    private LatestMediaVariantReader latestMediaVariantReader;

    @Test
    void readAll_returnsLatestLinkAndDerivativePerProfile() {
        MediaLink newestLink = MediaLink.create(101L, MediaOwnerType.ITEM, 200L, MediaUsageType.THUMBNAIL, 0);
        ReflectionTestUtils.setField(newestLink, "id", 1001L);
        ReflectionTestUtils.setField(newestLink, "createdAt", LocalDateTime.now());

        MediaLink olderLink = MediaLink.create(101L, MediaOwnerType.ITEM, 200L, MediaUsageType.GALLERY, 1);
        ReflectionTestUtils.setField(olderLink, "id", 1000L);
        ReflectionTestUtils.setField(olderLink, "createdAt", LocalDateTime.now().minusDays(1));

        MediaDerivative latestThumbnail = MediaDerivative.createReady(
                101L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                2L,
                "derived/latest-thumb.webp",
                "https://cdn.example.com/derived/latest-thumb.webp",
                640,
                360,
                "image/webp",
                12345L
        );
        ReflectionTestUtils.setField(latestThumbnail, "id", 9002L);

        MediaDerivative olderThumbnail = MediaDerivative.createReady(
                101L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                1L,
                "derived/older-thumb.webp",
                "https://cdn.example.com/derived/older-thumb.webp",
                640,
                360,
                "image/webp",
                12344L
        );
        ReflectionTestUtils.setField(olderThumbnail, "id", 9001L);

        MediaDerivative display = MediaDerivative.createReady(
                101L,
                MediaDerivativeProfile.DISPLAY_WEBP,
                1L,
                "derived/display.webp",
                "https://cdn.example.com/derived/display.webp",
                1280,
                720,
                "image/webp",
                22345L
        );
        ReflectionTestUtils.setField(display, "id", 9003L);

        when(mediaLinkRepository.findByMediaIdInOrderByMediaIdAscCreatedAtDesc(List.of(101L)))
                .thenReturn(List.of(newestLink, olderLink));
        when(mediaDerivativeRepository.findByMediaIdInAndDerivativeProfileInAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                List.of(101L),
                List.of(MediaDerivativeProfile.THUMBNAIL_WEBP, MediaDerivativeProfile.DISPLAY_WEBP),
                MediaDerivativeStatus.READY
        )).thenReturn(List.of(latestThumbnail, olderThumbnail, display));

        Map<Long, LatestMediaVariantView> result = latestMediaVariantReader.readAll(List.of(101L));

        LatestMediaVariantView latestVariant = result.get(101L);
        assertThat(latestVariant.latestLink()).isSameAs(newestLink);
        assertThat(latestVariant.latestDerivativesByProfile().get(MediaDerivativeProfile.THUMBNAIL_WEBP)).isSameAs(latestThumbnail);
        assertThat(latestVariant.latestDerivativesByProfile().get(MediaDerivativeProfile.DISPLAY_WEBP)).isSameAs(display);
    }

    @Test
    void read_returnsEmptyVariantWhenNothingExists() {
        when(mediaLinkRepository.findByMediaIdInOrderByMediaIdAscCreatedAtDesc(List.of(202L))).thenReturn(List.of());
        when(mediaDerivativeRepository.findByMediaIdInAndDerivativeProfileInAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                List.of(202L),
                List.of(MediaDerivativeProfile.THUMBNAIL_WEBP, MediaDerivativeProfile.DISPLAY_WEBP),
                MediaDerivativeStatus.READY
        )).thenReturn(List.of());

        LatestMediaVariantView result = latestMediaVariantReader.read(202L);

        assertThat(result.mediaId()).isEqualTo(202L);
        assertThat(result.latestLink()).isNull();
        assertThat(result.latestDerivativesByProfile()).isEmpty();
    }
}
