package com.example.clients.media.dto;

import java.util.List;
import java.util.Objects;

public record MediaLinksSyncCommand(
        String ownerType,
        Long ownerId,
        List<MediaUsageSet> sets
) {

    public MediaLinksSyncCommand {
        sets = sets == null ? List.of() : sets.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public record MediaUsageSet(
            String usageType,
            List<Long> mediaIds
    ) {
        public MediaUsageSet {
            mediaIds = mediaIds == null ? List.of() : mediaIds.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}
