package com.example.store.service.test.command;

import com.example.clients.media.dto.MediaLinksSyncCommand;
import com.example.clients.media.dto.MediaOwnerType;
import com.example.clients.media.dto.MediaUsageType;
import com.example.clients.media.exception.InvalidMediaReferenceException;
import com.example.clients.media.exception.MediaClientException;
import com.example.clients.media.facade.MediaClientFacade;
import com.example.core.exception.BusinessException;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.request.StoreUpdateRequest;
import com.example.store.entity.ImageType;
import com.example.store.exception.StoreErrorCode;
import com.example.store.service.command.StoreMediaReferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreMediaReferenceService 단위 테스트")
class StoreMediaReferenceServiceTest {

    @Mock private MediaClientFacade mediaClientFacade;

    @InjectMocks
    private StoreMediaReferenceService storeMediaReferenceService;

    private final Long STORE_ID = 100L;

    // ══════════════════════════════════════════════════════════════════════════
    // syncStoreImagesOnCreate
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("syncStoreImagesOnCreate - 가게 생성 시 이미지 동기화")
    class SyncOnCreate {

        @Test
        @DisplayName("images가 null이면 syncLinks 호출하지 않는다")
        void create_nullImages_doesNotSync() {
            storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, null);
            then(mediaClientFacade).should(never()).syncLinks(any());
        }

        @Test
        @DisplayName("images가 빈 리스트이면 syncLinks 호출하지 않는다")
        void create_emptyImages_doesNotSync() {
            storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, List.of());
            then(mediaClientFacade).should(never()).syncLinks(any());
        }

        @Test
        @DisplayName("THUMBNAIL mediaId가 있으면 THUMBNAIL usageSet에 포함된다")
        void create_thumbnailMediaId_includedInThumbnailUsageSet() {
            // given
            List<StoreCreateRequest.StoreImageRequest> images = List.of(
                new StoreCreateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            MediaLinksSyncCommand command = captor.getValue();
            assertThat(command.ownerType()).isEqualTo(MediaOwnerType.STORE);
            assertThat(command.ownerId()).isEqualTo(STORE_ID);

            List<Long> thumbnailIds = getMediaIds(command, MediaUsageType.THUMBNAIL);
            assertThat(thumbnailIds).containsExactly(1L);
        }

        @Test
        @DisplayName("GALLERY mediaId가 있으면 GALLERY usageSet에 포함된다")
        void create_galleryMediaId_includedInGalleryUsageSet() {
            // given
            List<StoreCreateRequest.StoreImageRequest> images = List.of(
                new StoreCreateRequest.StoreImageRequest(ImageType.GALLERY, 2L, 0),
                new StoreCreateRequest.StoreImageRequest(ImageType.GALLERY, 3L, 1)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            List<Long> galleryIds = getMediaIds(captor.getValue(), MediaUsageType.GALLERY);
            assertThat(galleryIds).containsExactlyInAnyOrder(2L, 3L);
        }

        @Test
        @DisplayName("mediaId가 null인 항목은 usageSet에 포함되지 않는다")
        void create_nullMediaId_excludedFromUsageSet() {
            // given
            List<StoreCreateRequest.StoreImageRequest> images = List.of(
                new StoreCreateRequest.StoreImageRequest(ImageType.THUMBNAIL, null, 0),
                new StoreCreateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L,   1)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            List<Long> thumbnailIds = getMediaIds(captor.getValue(), MediaUsageType.THUMBNAIL);
            assertThat(thumbnailIds).containsExactly(1L); // null 제외
        }

        @Test
        @DisplayName("모든 mediaId가 null이면 usageSet은 빈 리스트로 전달된다")
        void create_allNullMediaIds_emptyUsageSets() {
            // given
            List<StoreCreateRequest.StoreImageRequest> images = List.of(
                new StoreCreateRequest.StoreImageRequest(ImageType.THUMBNAIL, null, 0)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            List<Long> thumbnailIds = getMediaIds(captor.getValue(), MediaUsageType.THUMBNAIL);
            assertThat(thumbnailIds).isEmpty();
        }

        @Test
        @DisplayName("THUMBNAIL + GALLERY 혼합 시 타입별로 분리되어 전달된다")
        void create_mixedTypes_separatedByType() {
            // given
            List<StoreCreateRequest.StoreImageRequest> images = List.of(
                new StoreCreateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0),
                new StoreCreateRequest.StoreImageRequest(ImageType.GALLERY,   2L, 0),
                new StoreCreateRequest.StoreImageRequest(ImageType.GALLERY,   3L, 1)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            assertThat(getMediaIds(captor.getValue(), MediaUsageType.THUMBNAIL)).containsExactly(1L);
            assertThat(getMediaIds(captor.getValue(), MediaUsageType.GALLERY)).containsExactlyInAnyOrder(2L, 3L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // syncStoreImagesOnUpdate
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("syncStoreImagesOnUpdate - 가게 수정 시 이미지 동기화")
    class SyncOnUpdate {

        @Test
        @DisplayName("images가 null이면 syncLinks 호출하지 않는다 (이미지 변경 없음)")
        void update_nullImages_doesNotSync() {
            storeMediaReferenceService.syncStoreImagesOnUpdate(STORE_ID, null);
            then(mediaClientFacade).should(never()).syncLinks(any());
        }

        @Test
        @DisplayName("images가 빈 리스트이면 syncLinks를 호출한다 (전체 해제 신호)")
        void update_emptyImages_callsSync() {
            storeMediaReferenceService.syncStoreImagesOnUpdate(STORE_ID, List.of());
            then(mediaClientFacade).should(times(1)).syncLinks(any());
        }

        @Test
        @DisplayName("mediaId가 null인 항목은 해당 타입 삭제 신호 → 빈 리스트로 전달된다")
        void update_nullMediaId_emptyUsageSet() {
            // given
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, null, 0)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnUpdate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            List<Long> thumbnailIds = getMediaIds(captor.getValue(), MediaUsageType.THUMBNAIL);
            assertThat(thumbnailIds).isEmpty(); // null 제거 → 빈 리스트 = 삭제
        }

        @Test
        @DisplayName("mediaId가 있으면 해당 id가 usageSet에 포함된다")
        void update_validMediaId_includedInUsageSet() {
            // given
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, 10L, 0)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnUpdate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            List<Long> thumbnailIds = getMediaIds(captor.getValue(), MediaUsageType.THUMBNAIL);
            assertThat(thumbnailIds).containsExactly(10L);
        }

        @Test
        @DisplayName("THUMBNAIL null + GALLERY 값 있음 → THUMBNAIL 빈 리스트, GALLERY ids 포함")
        void update_thumbnailNull_galleryValid_separatedCorrectly() {
            // given
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, null, 0),
                new StoreUpdateRequest.StoreImageRequest(ImageType.GALLERY,   20L,  0),
                new StoreUpdateRequest.StoreImageRequest(ImageType.GALLERY,   21L,  1)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnUpdate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            assertThat(getMediaIds(captor.getValue(), MediaUsageType.THUMBNAIL)).isEmpty();
            assertThat(getMediaIds(captor.getValue(), MediaUsageType.GALLERY)).containsExactlyInAnyOrder(20L, 21L);
        }

        @Test
        @DisplayName("command의 ownerType은 STORE, ownerId는 storeId이다")
        void update_commandOwner_isStoreAndStoreId() {
            // given
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0)
            );

            // when
            storeMediaReferenceService.syncStoreImagesOnUpdate(STORE_ID, images);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            assertThat(captor.getValue().ownerType()).isEqualTo(MediaOwnerType.STORE);
            assertThat(captor.getValue().ownerId()).isEqualTo(STORE_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // clearStoreImageLinks
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("clearStoreImageLinks - 가게 삭제 시 이미지 링크 전체 해제")
    class ClearLinks {

        @Test
        @DisplayName("syncLinks 1회 호출된다")
        void clear_callsSyncLinksOnce() {
            storeMediaReferenceService.clearStoreImageLinks(STORE_ID);
            then(mediaClientFacade).should(times(1)).syncLinks(any());
        }

        @Test
        @DisplayName("command usageSets가 빈 리스트로 전달된다 (전체 해제)")
        void clear_emptyUsageSets_sentToSync() {
            // when
            storeMediaReferenceService.clearStoreImageLinks(STORE_ID);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            assertThat(captor.getValue().sets()).isEmpty();
        }

        @Test
        @DisplayName("command의 ownerType은 STORE, ownerId는 storeId이다")
        void clear_commandOwner_isStoreAndStoreId() {
            // when
            storeMediaReferenceService.clearStoreImageLinks(STORE_ID);

            // then
            ArgumentCaptor<MediaLinksSyncCommand> captor = ArgumentCaptor.forClass(MediaLinksSyncCommand.class);
            then(mediaClientFacade).should(times(1)).syncLinks(captor.capture());

            assertThat(captor.getValue().ownerType()).isEqualTo(MediaOwnerType.STORE);
            assertThat(captor.getValue().ownerId()).isEqualTo(STORE_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 예외 케이스 (doSyncLinks)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("doSyncLinks - 예외 처리")
    class SyncException {

        @Test
        @DisplayName("InvalidMediaReferenceException 발생 시 IMAGE_UPLOAD_FAILED BusinessException으로 변환")
        void syncLinks_invalidMediaRef_throwsBusinessException() {
            // given
            willThrow(new InvalidMediaReferenceException("잘못된 mediaId"))
                .given(mediaClientFacade).syncLinks(any());

            List<StoreCreateRequest.StoreImageRequest> images = List.of(
                new StoreCreateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0)
            );

            // when & then
            assertThatThrownBy(() ->
                storeMediaReferenceService.syncStoreImagesOnCreate(STORE_ID, images))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(StoreErrorCode.IMAGE_UPLOAD_FAILED));
        }

        @Test
        @DisplayName("MediaClientException 발생 시 IMAGE_UPLOAD_FAILED BusinessException으로 변환")
        void syncLinks_mediaClientException_throwsBusinessException() {
            // given
            willThrow(new MediaClientException("media 서비스 오류"))
                .given(mediaClientFacade).syncLinks(any());

            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0)
            );

            // when & then
            assertThatThrownBy(() ->
                storeMediaReferenceService.syncStoreImagesOnUpdate(STORE_ID, images))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(StoreErrorCode.IMAGE_UPLOAD_FAILED));
        }

        @Test
        @DisplayName("clearStoreImageLinks에서 MediaClientException 발생 시 BusinessException으로 변환")
        void clear_mediaClientException_throwsBusinessException() {
            // given
            willThrow(new MediaClientException("media 서비스 오류"))
                .given(mediaClientFacade).syncLinks(any());

            // when & then
            assertThatThrownBy(() ->
                storeMediaReferenceService.clearStoreImageLinks(STORE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(StoreErrorCode.IMAGE_UPLOAD_FAILED));
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private List<Long> getMediaIds(MediaLinksSyncCommand command, MediaUsageType usageType) {
        return command.sets().stream()
            .filter(set -> set.usageType() == usageType)
            .findFirst()
            .map(MediaLinksSyncCommand.MediaUsageSet::mediaIds)
            .orElse(List.of());
    }
}
