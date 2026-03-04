package com.example.clients.media.dto;

import java.util.List;
import java.util.Objects;

public record MediaLinksSyncCommand(
        MediaOwnerType ownerType,
        Long ownerId,
        List<MediaUsageSet> sets
) {

    public MediaLinksSyncCommand {
        sets = sets == null ? List.of() : sets.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public record MediaUsageSet(
            MediaUsageType usageType,
            List<Long> mediaIds
    ) {
        public MediaUsageSet {
            mediaIds = mediaIds == null ? List.of() : mediaIds.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}
