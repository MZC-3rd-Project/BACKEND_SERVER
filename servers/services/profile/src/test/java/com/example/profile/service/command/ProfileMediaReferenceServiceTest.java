package com.example.profile.service.command;

import com.example.clients.media.dto.MediaLinksSyncCommand;
import com.example.clients.media.dto.MediaOwnerType;
import com.example.clients.media.dto.MediaUsageType;
import com.example.clients.media.exception.InvalidMediaReferenceException;
import com.example.clients.media.exception.MediaClientException;
import com.example.clients.media.facade.MediaClientFacade;
import com.example.core.exception.BusinessException;
import com.example.profile.exception.ProfileErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileMediaReferenceServiceTest {

    @Mock
    private MediaClientFacade mediaClientFacade;

    private ProfileMediaReferenceService profileMediaReferenceService;

    @BeforeEach
    void setUp() {
        profileMediaReferenceService = new ProfileMediaReferenceService(mediaClientFacade);
    }

    @Test
    void resolveCanonicalMediaId_returnsMediaIdWhenNumericValueProvided() {
        Long mediaId = profileMediaReferenceService.resolveCanonicalMediaId(101L, null);

        assertThat(mediaId).isEqualTo(101L);
    }

    @Test
    void resolveCanonicalMediaId_convertsLegacyRefWithPrefix() {
        Long mediaId = profileMediaReferenceService.resolveCanonicalMediaId(null, "media-202");

        assertThat(mediaId).isEqualTo(202L);
    }

    @Test
    void resolveCanonicalMediaId_throwsWhenLegacyRefIsInvalid() {
        assertThatThrownBy(() -> profileMediaReferenceService.resolveCanonicalMediaId(null, "legacy-image-ref"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ProfileErrorCode.INVALID_MEDIA_ID);
    }

    @Test
    void validateReadableMedia_mapsInvalidReferenceToBusinessError() {
        doThrow(new InvalidMediaReferenceException("invalid"))
            .when(mediaClientFacade).getMediaUrl(303L);

        assertThatThrownBy(() -> profileMediaReferenceService.validateReadableMedia(303L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ProfileErrorCode.MEDIA_VALIDATION_FAILED);
    }

    @Test
    void syncProfileImageLink_mapsCommunicationFailureToBusinessError() {
        doThrow(new MediaClientException("downstream error"))
            .when(mediaClientFacade).syncLinks(org.mockito.ArgumentMatchers.any(MediaLinksSyncCommand.class));

        assertThatThrownBy(() -> profileMediaReferenceService.syncProfileImageLink(1L, 99L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ProfileErrorCode.MEDIA_SERVICE_COMMUNICATION_ERROR);
    }

    @Test
    void syncProfileImageLink_sendsOwnerAndUsageContract() {
        profileMediaReferenceService.syncProfileImageLink(11L, 400L);

        ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
        verify(mediaClientFacade).syncLinks(captor.capture());

        MediaLinksSyncCommand command = captor.getValue();
        assertThat(command.ownerType()).isEqualTo(MediaOwnerType.USER_PROFILE);
        assertThat(command.ownerId()).isEqualTo(11L);
        assertThat(command.sets()).hasSize(1);
        assertThat(command.sets().get(0).usageType()).isEqualTo(MediaUsageType.THUMBNAIL);
        assertThat(command.sets().get(0).mediaIds()).containsExactly(400L);
    }

    @Test
    void clearProfileLinks_sendsEmptySetsForOwnerWideUnlink() {
        profileMediaReferenceService.clearProfileLinks(12L);

        ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
        verify(mediaClientFacade).syncLinks(captor.capture());

        MediaLinksSyncCommand command = captor.getValue();
        assertThat(command.ownerType()).isEqualTo(MediaOwnerType.USER_PROFILE);
        assertThat(command.ownerId()).isEqualTo(12L);
        assertThat(command.sets()).isEmpty();
    }
}
