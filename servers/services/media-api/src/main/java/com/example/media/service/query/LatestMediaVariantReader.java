package com.example.media.service.query;

import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaDerivativeStatus;
import com.example.media.entity.MediaLink;
import com.example.media.repository.MediaDerivativeRepository;
import com.example.media.repository.MediaLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LatestMediaVariantReader {

    private static final List<MediaDerivativeProfile> SERVING_DERIVATIVE_PROFILES = List.of(
            MediaDerivativeProfile.THUMBNAIL_WEBP,
            MediaDerivativeProfile.DISPLAY_WEBP
    );

    private final MediaLinkRepository mediaLinkRepository;
    private final MediaDerivativeRepository mediaDerivativeRepository;

    public LatestMediaVariantView read(Long mediaId) {
        return readAll(List.of(mediaId)).getOrDefault(mediaId, LatestMediaVariantView.empty(mediaId));
    }

    public Map<Long, LatestMediaVariantView> readAll(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Map.of();
        }

        List<Long> uniqueIds = mediaIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, MediaLink> latestLinkByMediaId = mediaLinkRepository
                .findByMediaIdInOrderByMediaIdAscCreatedAtDesc(uniqueIds)
                .stream()
                .collect(Collectors.toMap(MediaLink::getMediaId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        Map<Long, Map<MediaDerivativeProfile, MediaDerivative>> derivativesByMediaAndProfile =
                readLatestReadyDerivatives(uniqueIds);

        Map<Long, LatestMediaVariantView> result = new LinkedHashMap<>();
        for (Long mediaId : uniqueIds) {
            result.put(
                    mediaId,
                    new LatestMediaVariantView(
                            mediaId,
                            latestLinkByMediaId.get(mediaId),
                            Map.copyOf(derivativesByMediaAndProfile.getOrDefault(mediaId, Map.of()))
                    )
            );
        }
        return result;
    }

    private Map<Long, Map<MediaDerivativeProfile, MediaDerivative>> readLatestReadyDerivatives(List<Long> mediaIds) {
        List<MediaDerivative> readyDerivatives = mediaDerivativeRepository
                .findByMediaIdInAndDerivativeProfileInAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                        mediaIds,
                        SERVING_DERIVATIVE_PROFILES,
                        MediaDerivativeStatus.READY
                );

        Map<Long, Map<MediaDerivativeProfile, MediaDerivative>> result = new LinkedHashMap<>();
        for (MediaDerivative derivative : readyDerivatives) {
            Map<MediaDerivativeProfile, MediaDerivative> derivativeByProfile = result.computeIfAbsent(
                    derivative.getMediaId(),
                    ignored -> new LinkedHashMap<>()
            );
            derivativeByProfile.putIfAbsent(derivative.getDerivativeProfile(), derivative);
        }
        return result;
    }
}
