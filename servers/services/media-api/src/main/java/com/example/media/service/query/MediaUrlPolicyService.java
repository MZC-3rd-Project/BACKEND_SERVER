package com.example.media.service.query;

import com.example.media.config.MediaS3Properties;
import com.example.media.config.MediaUrlAccessType;
import com.example.media.config.MediaUrlProperties;
import com.example.media.entity.MediaUsageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MediaUrlPolicyService {

    private final MediaS3Properties mediaS3Properties;
    private final MediaUrlProperties mediaUrlProperties;

    public MediaUrlContract resolve(String objectKey, MediaUsageType usageType) {
        String url = buildBaseUrl(objectKey);
        MediaUrlAccessType accessType = mediaUrlProperties.getAccessType();
        Instant expiresAt = null;
        if (accessType == MediaUrlAccessType.SIGNED_URL) {
            expiresAt = Instant.now().plusSeconds(Math.max(1, mediaUrlProperties.getSignedUrlTtlSeconds()));
        }
        String cacheControl = usageType == MediaUsageType.THUMBNAIL
                ? mediaUrlProperties.getThumbnailCacheControl()
                : mediaUrlProperties.getDefaultCacheControl();
        return new MediaUrlContract(url, accessType, expiresAt, cacheControl);
    }

    public String buildBaseUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }

        if (StringUtils.hasText(mediaS3Properties.getCloudfrontDomain())) {
            String domain = mediaS3Properties.getCloudfrontDomain().trim();
            if (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }
            return domain + "/" + objectKey;
        }

        if (!StringUtils.hasText(mediaS3Properties.getBucket())
                || !StringUtils.hasText(mediaS3Properties.getRegion())) {
            return null;
        }
        return "https://" + mediaS3Properties.getBucket().trim()
                + ".s3."
                + mediaS3Properties.getRegion().trim()
                + ".amazonaws.com/"
                + objectKey;
    }

    public record MediaUrlContract(
            String url,
            MediaUrlAccessType accessType,
            Instant expiresAt,
            String cacheControl
    ) {
    }
}
