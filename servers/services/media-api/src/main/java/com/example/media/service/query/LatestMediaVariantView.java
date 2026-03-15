package com.example.media.service.query;

import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaLink;

import java.util.Map;

public record LatestMediaVariantView(
        Long mediaId,
        MediaLink latestLink,
        Map<MediaDerivativeProfile, MediaDerivative> latestDerivativesByProfile
) {

    public static LatestMediaVariantView empty(Long mediaId) {
        return new LatestMediaVariantView(mediaId, null, Map.of());
    }
}
