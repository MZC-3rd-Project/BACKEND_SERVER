package com.example.media.service.query;

import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaUsageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MediaUrlAssembler {

    private static final MediaDerivativeProfile THUMBNAIL_PROFILE = MediaDerivativeProfile.THUMBNAIL_WEBP;
    private static final MediaDerivativeProfile DISPLAY_PROFILE = MediaDerivativeProfile.DISPLAY_WEBP;

    private final MediaUrlPolicyService mediaUrlPolicyService;

    public MediaUrlResponse assemble(MediaFile mediaFile, LatestMediaVariantView latestVariant) {
        MediaLink latestLink = latestVariant != null ? latestVariant.latestLink() : null;
        Map<MediaDerivativeProfile, MediaDerivative> derivativesByProfile =
                latestVariant != null ? latestVariant.latestDerivativesByProfile() : Map.of();

        String resolvedObjectKey = resolveObjectKey(mediaFile, latestLink, derivativesByProfile);
        MediaUsageType resolvedUsageType = resolveUsageType(latestLink);
        MediaUrlPolicyService.MediaUrlContract urlContract = mediaUrlPolicyService.resolve(
                resolvedObjectKey,
                resolvedUsageType
        );

        return MediaUrlResponse.builder()
                .mediaId(mediaFile.getId())
                .status(mediaFile.getStatus().name())
                .objectKey(resolvedObjectKey)
                .mediaUrl(urlContract.url())
                .urlAccessType(urlContract.accessType().name())
                .urlExpiresAt(urlContract.expiresAt())
                .cacheControl(urlContract.cacheControl())
                .usageType(resolvedUsageType != null ? resolvedUsageType.name() : null)
                .build();
    }

    private String resolveObjectKey(MediaFile mediaFile,
                                    MediaLink latestLink,
                                    Map<MediaDerivativeProfile, MediaDerivative> derivativesByProfile) {
        MediaDerivative preferredDerivative = resolvePreferredDerivative(latestLink, derivativesByProfile);
        if (preferredDerivative != null) {
            return preferredDerivative.getObjectKey();
        }
        return mediaFile.getObjectKey();
    }

    private MediaUsageType resolveUsageType(MediaLink latestLink) {
        if (latestLink == null) {
            return null;
        }
        return latestLink.getUsageType();
    }

    private MediaDerivative resolvePreferredDerivative(MediaLink latestLink,
                                                       Map<MediaDerivativeProfile, MediaDerivative> derivativesByProfile) {
        if (derivativesByProfile == null || derivativesByProfile.isEmpty()) {
            return null;
        }

        MediaUsageType usageType = resolveUsageType(latestLink);
        if (usageType == MediaUsageType.THUMBNAIL) {
            MediaDerivative thumbnail = derivativesByProfile.get(THUMBNAIL_PROFILE);
            if (thumbnail != null) {
                return thumbnail;
            }
        }

        return derivativesByProfile.get(DISPLAY_PROFILE);
    }
}
