package com.example.media.service.query;

import com.example.core.exception.BusinessException;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaStatus;
import com.example.media.exception.MediaErrorCode;
import com.example.media.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaQueryService {

    private final MediaFileRepository mediaFileRepository;
    private final MediaAccessPolicy mediaAccessPolicy;
    private final LatestMediaVariantReader latestMediaVariantReader;
    private final MediaUrlAssembler mediaUrlAssembler;

    @Transactional(readOnly = true)
    public MediaUrlResponse getMediaUrl(Long mediaId, MediaAccessContext accessContext) {
        MediaFile mediaFile = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));

        validateAccess(mediaFile, accessContext);
        validateStatus(mediaFile);

        LatestMediaVariantView latestVariant = latestMediaVariantReader.read(mediaId);
        return mediaUrlAssembler.assemble(mediaFile, latestVariant);
    }

    @Transactional(readOnly = true)
    public List<MediaUrlResponse> getMediaUrls(List<Long> mediaIds, MediaAccessContext accessContext) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }

        List<Long> uniqueIds = mediaIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MediaFile> mediaFileMap = mediaFileRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(MediaFile::getId, Function.identity()));

        Map<Long, LatestMediaVariantView> latestVariantByMediaId = latestMediaVariantReader.readAll(uniqueIds);

        return uniqueIds.stream()
                .map(mediaFileMap::get)
                .filter(Objects::nonNull)
                .filter(mediaFile -> isAccessibleAndReady(mediaFile, accessContext))
                .map(mediaFile -> {
                    LatestMediaVariantView latestVariant = latestVariantByMediaId.getOrDefault(
                            mediaFile.getId(),
                            LatestMediaVariantView.empty(mediaFile.getId())
                    );
                    return mediaUrlAssembler.assemble(mediaFile, latestVariant);
                })
                .toList();
    }

    private void validateAccess(MediaFile mediaFile, MediaAccessContext accessContext) {
        mediaAccessPolicy.validateReadAccess(mediaFile, accessContext);
    }

    private void validateStatus(MediaFile mediaFile) {
        if (mediaFile.getStatus() != MediaStatus.CONFIRMED && mediaFile.getStatus() != MediaStatus.READY) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_READY);
        }
    }

    private boolean isAccessibleAndReady(MediaFile mediaFile, MediaAccessContext accessContext) {
        try {
            validateAccess(mediaFile, accessContext);
            validateStatus(mediaFile);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }
}
