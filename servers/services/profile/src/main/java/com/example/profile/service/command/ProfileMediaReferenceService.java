package com.example.profile.service.command;

import com.example.clients.media.dto.MediaLinksSyncCommand;
import com.example.clients.media.dto.MediaOwnerType;
import com.example.clients.media.dto.MediaUsageType;
import com.example.clients.media.exception.InvalidMediaReferenceException;
import com.example.clients.media.exception.MediaClientException;
import com.example.clients.media.facade.MediaClientFacade;
import com.example.core.exception.BusinessException;
import com.example.profile.exception.ProfileErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProfileMediaReferenceService {

    private static final Pattern LEGACY_MEDIA_REF_PATTERN = Pattern.compile("(?i)^media[-_:]?(\\d+)$");

    private final MediaClientFacade mediaClientFacade;

    public Long resolveCanonicalMediaId(Long mediaId, String mediaRef) {
        if (mediaId != null) {
            return ensurePositiveMediaId(mediaId);
        }

        if (!StringUtils.hasText(mediaRef)) {
            return null;
        }

        String normalizedRef = mediaRef.trim();
        if (normalizedRef.chars().allMatch(Character::isDigit)) {
            return ensurePositiveMediaId(parseLong(normalizedRef));
        }

        Matcher matcher = LEGACY_MEDIA_REF_PATTERN.matcher(normalizedRef);
        if (matcher.matches()) {
            return ensurePositiveMediaId(parseLong(matcher.group(1)));
        }

        throw new BusinessException(ProfileErrorCode.INVALID_MEDIA_ID);
    }

    public void validateReadableMedia(Long mediaId) {
        if (mediaId == null) {
            return;
        }

        try {
            mediaClientFacade.getMediaUrl(mediaId);
        } catch (InvalidMediaReferenceException e) {
            throw new BusinessException(ProfileErrorCode.MEDIA_VALIDATION_FAILED, e);
        } catch (MediaClientException e) {
            throw new BusinessException(ProfileErrorCode.MEDIA_SERVICE_COMMUNICATION_ERROR, e);
        }
    }

    public void syncProfileImageLink(Long userId, Long mediaId) {
        List<Long> mediaIds = mediaId == null ? List.of() : List.of(mediaId);
        MediaLinksSyncCommand command = new MediaLinksSyncCommand(
                MediaOwnerType.USER_PROFILE,
                userId,
                List.of(new MediaLinksSyncCommand.MediaUsageSet(MediaUsageType.THUMBNAIL, mediaIds))
        );

        doSyncLinks(command);
    }

    public void clearProfileLinks(Long userId) {
        MediaLinksSyncCommand command = new MediaLinksSyncCommand(
                MediaOwnerType.USER_PROFILE,
                userId,
                List.of()
        );

        doSyncLinks(command);
    }

    private void doSyncLinks(MediaLinksSyncCommand command) {

        try {
            mediaClientFacade.syncLinks(command);
        } catch (InvalidMediaReferenceException e) {
            throw new BusinessException(ProfileErrorCode.MEDIA_VALIDATION_FAILED, e);
        } catch (MediaClientException e) {
            throw new BusinessException(ProfileErrorCode.MEDIA_SERVICE_COMMUNICATION_ERROR, e);
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ProfileErrorCode.INVALID_MEDIA_ID, e);
        }
    }

    private Long ensurePositiveMediaId(Long mediaId) {
        if (mediaId == null || mediaId <= 0) {
            throw new BusinessException(ProfileErrorCode.INVALID_MEDIA_ID);
        }
        return mediaId;
    }
}
