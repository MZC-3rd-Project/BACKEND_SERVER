package com.example.clients.media.impl;

import com.example.clients.media.dto.MediaLinksSyncCommand;
import com.example.clients.media.exception.InvalidMediaReferenceException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class MediaClientValidator {

    public void validateMediaId(Long mediaId) {
        if (mediaId == null || mediaId <= 0) {
            throw new InvalidMediaReferenceException("mediaId must be positive");
        }
    }

    public List<Long> normalizeMediaIds(Collection<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long mediaId : mediaIds) {
            validateMediaId(mediaId);
            normalized.add(mediaId);
        }
        return normalized.stream().toList();
    }

    public MediaLinksSyncCommand normalizeSyncCommand(MediaLinksSyncCommand command) {
        if (command == null || command.ownerType() == null || command.ownerId() == null || command.ownerId() <= 0) {
            throw new InvalidMediaReferenceException("syncLinks command is invalid");
        }

        List<MediaLinksSyncCommand.MediaUsageSet> normalizedSets = command.sets().stream()
                .filter(Objects::nonNull)
                .map(set -> {
                    if (set.usageType() == null) {
                        throw new InvalidMediaReferenceException("usageType is required");
                    }
                    return new MediaLinksSyncCommand.MediaUsageSet(
                            set.usageType(),
                            normalizeMediaIds(set.mediaIds())
                    );
                })
                .toList();

        return new MediaLinksSyncCommand(command.ownerType(), command.ownerId(), normalizedSets);
    }
}
