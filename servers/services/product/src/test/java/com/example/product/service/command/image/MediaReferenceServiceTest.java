package com.example.product.service.command.image;

import com.example.clients.media.InvalidMediaReferenceException;
import com.example.clients.media.MediaClientException;
import com.example.clients.media.MediaClientFacade;
import com.example.clients.media.MediaClientValidator;
import com.example.clients.media.MediaLinksSyncCommand;
import com.example.core.exception.BusinessException;
import com.example.product.exception.ProductErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MediaReferenceServiceTest {

    @Mock
    private MediaClientFacade mediaClientFacade;

    @Mock
    private MediaClientValidator mediaClientValidator;

    @InjectMocks
    private MediaReferenceService mediaReferenceService;

    @BeforeEach
    void setUp() {
        lenient().when(mediaClientValidator.normalizeMediaIds(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().doNothing().when(mediaClientValidator).validateMediaId(anyLong());
    }

    @Test
    void resolveMediaUrl_returnsNull_whenMediaIdIsNull() {
        String mediaUrl = mediaReferenceService.resolveMediaUrl(null);

        assertThat(mediaUrl).isNull();
        verifyNoInteractions(mediaClientFacade);
    }

    @Test
    void resolveMediaUrl_mapsInvalidReferenceToBusinessException() {
        when(mediaClientFacade.getMediaUrl(10L)).thenThrow(new InvalidMediaReferenceException("invalid"));

        assertThatThrownBy(() -> mediaReferenceService.resolveMediaUrl(10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_MEDIA_REFERENCE)
                );
    }

    @Test
    void resolveMediaUrl_mapsMediaClientFailureToBusinessException() {
        when(mediaClientFacade.getMediaUrl(10L)).thenThrow(new MediaClientException("downstream failed"));

        assertThatThrownBy(() -> mediaReferenceService.resolveMediaUrl(10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProductErrorCode.MEDIA_SERVICE_ERROR)
                );
    }

    @Test
    void syncItemMediaLinks_buildsExpectedCommand() {
        mediaReferenceService.syncItemMediaLinks(100L, 10L, List.of(20L, 21L));

        ArgumentCaptor<MediaLinksSyncCommand> commandCaptor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
        verify(mediaClientFacade).syncLinks(commandCaptor.capture());

        MediaLinksSyncCommand command = commandCaptor.getValue();
        assertThat(command.ownerType()).isEqualTo("ITEM");
        assertThat(command.ownerId()).isEqualTo(100L);
        assertThat(command.sets()).hasSize(2);

        assertThat(command.sets().get(0).usageType()).isEqualTo("THUMBNAIL");
        assertThat(command.sets().get(0).mediaIds()).containsExactly(10L);
        assertThat(command.sets().get(1).usageType()).isEqualTo("GALLERY");
        assertThat(command.sets().get(1).mediaIds()).containsExactly(20L, 21L);
    }

    @Test
    void syncItemMediaLinks_mapsInvalidGalleryIdsToBusinessException() {
        when(mediaClientValidator.normalizeMediaIds(java.util.Arrays.asList(20L, null, 21L)))
                .thenThrow(new InvalidMediaReferenceException("invalid"));

        assertThatThrownBy(() -> mediaReferenceService.syncItemMediaLinks(100L, 10L, java.util.Arrays.asList(20L, null, 21L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_MEDIA_REFERENCE)
                );
    }

    @Test
    void validateMediaReferences_mapsValidatorFailureToBusinessException() {
        java.util.List<Long> invalidIds = java.util.Arrays.asList(10L, null);
        when(mediaClientValidator.normalizeMediaIds(invalidIds)).thenThrow(new InvalidMediaReferenceException("invalid"));

        assertThatThrownBy(() -> mediaReferenceService.validateMediaReferences(invalidIds))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_MEDIA_REFERENCE)
                );
    }
}
