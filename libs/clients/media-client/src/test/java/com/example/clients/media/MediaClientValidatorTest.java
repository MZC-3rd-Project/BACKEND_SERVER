package com.example.clients.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaClientValidatorTest {

    private final MediaClientValidator validator = new MediaClientValidator();

    @Test
    void normalizeMediaIds_deduplicatesWhilePreservingOrder() {
        List<Long> normalized = validator.normalizeMediaIds(List.of(11L, 10L, 11L, 12L));

        assertThat(normalized).containsExactly(11L, 10L, 12L);
    }

    @Test
    void normalizeMediaIds_throwsWhenInvalidIdExists() {
        assertThatThrownBy(() -> validator.normalizeMediaIds(List.of(10L, 0L)))
                .isInstanceOf(InvalidMediaReferenceException.class);
    }

    @Test
    void normalizeSyncCommand_throwsWhenOwnerInfoIsMissing() {
        MediaLinksSyncCommand command = new MediaLinksSyncCommand(null, 10L, List.of());

        assertThatThrownBy(() -> validator.normalizeSyncCommand(command))
                .isInstanceOf(InvalidMediaReferenceException.class);
    }

    @Test
    void normalizeSyncCommand_normalizesSetMediaIds() {
        MediaLinksSyncCommand command = new MediaLinksSyncCommand(
                "ITEM",
                100L,
                List.of(new MediaLinksSyncCommand.MediaUsageSet("GALLERY", List.of(20L, 21L, 20L)))
        );

        MediaLinksSyncCommand normalized = validator.normalizeSyncCommand(command);

        assertThat(normalized.sets()).hasSize(1);
        assertThat(normalized.sets().get(0).mediaIds()).containsExactly(20L, 21L);
    }
}
