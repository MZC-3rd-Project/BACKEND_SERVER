package com.example.store.service.test.command;

import com.example.store.dto.request.StoreUpdateRequest;
import com.example.store.entity.*;
import com.example.store.repository.*;
import com.example.store.service.command.StoreCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreImage mediaId 저장 테스트")
class StoreImageSaveTest {

    @Mock private StoresRepository       storesRepository;
    @Mock private StoreProfileRepository storeProfileRepository;
    @Mock private StoreAddressRepository storeAddressRepository;
    @Mock private StoreContactRepository storeContactRepository;
    @Mock private StoreImageRepository   storeImageRepository;

    @InjectMocks
    private StoreCommandService storeCommandService;

    // ── 픽스처 ─────────────────────────────────────────────────────────────────

    private final Long USER_ID  = 1L;
    private final Long STORE_ID = 100L;

    private Stores mockStore() {
        return Stores.builder()
            .id(STORE_ID)
            .userId(USER_ID)
            .storeName("테스트 가게")
            .status(StoreStatus.INACTIVE)
            .build();
    }

    private StoreAddress mockAddress() {
        return StoreAddress.builder()
            .store(mockStore())
            .address("서울시 강남구")
            .addressType(AddressType.MAIN)
            .isDefault(true)
            .build();
    }

    private StoreContact mockContact() {
        return StoreContact.builder()
            .store(mockStore())
            .contactValue("010-0000-0000")
            .contactType(ContactType.PHONE)
            .isPrimary(true)
            .build();
    }

    private void givenBaseMocks() {
        given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
            .willReturn(Optional.of(mockStore()));
        given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID))
            .willReturn(Optional.of(mockAddress()));
        given(storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(STORE_ID))
            .willReturn(Optional.of(mockContact()));
        given(storeProfileRepository.findByStoreId(STORE_ID))
            .willReturn(Optional.empty());
    }

    private StoreUpdateRequest requestWithImages(List<StoreUpdateRequest.StoreImageRequest> images) {
        return new StoreUpdateRequest(
            null, null, null, null, null, null, null,
            images
        );
    }

    // ── mediaId 저장 테스트 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("mediaId 저장")
    class MediaIdSave {

        @Test
        @DisplayName("mediaId가 정상 값이면 그대로 저장된다")
        void save_withValidMediaId_savedAsIs() {
            // given
            givenBaseMocks();
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0)
            );

            // when
            storeCommandService.update(USER_ID, STORE_ID, requestWithImages(images));

            // then - saveAll 호출 검증
            ArgumentCaptor<List<StoreImage>> captor = ArgumentCaptor.forClass(List.class);
            then(storeImageRepository).should(times(1)).saveAll(captor.capture());

            List<StoreImage> saved = captor.getValue();
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getMediaId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("mediaId가 null이면 null 그대로 저장된다")
        void save_withNullMediaId_savedAsNull() {
            // given
            givenBaseMocks();
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, null, 0)
            );

            // when
            storeCommandService.update(USER_ID, STORE_ID, requestWithImages(images));

            // then
            ArgumentCaptor<List<StoreImage>> captor = ArgumentCaptor.forClass(List.class);
            then(storeImageRepository).should(times(1)).saveAll(captor.capture());

            List<StoreImage> saved = captor.getValue();
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getMediaId()).isNull();
        }

        @Test
        @DisplayName("mediaId가 null인 이미지와 정상 이미지가 함께 있으면 모두 저장된다")
        void save_withMixedMediaId_allSaved() {
            // given
            givenBaseMocks();
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0),
                new StoreUpdateRequest.StoreImageRequest(ImageType.BANNER,    null,        1),
                new StoreUpdateRequest.StoreImageRequest(ImageType.INTRODUCE, 100L, 2)
            );

            // when
            storeCommandService.update(USER_ID, STORE_ID, requestWithImages(images));

            // then
            ArgumentCaptor<List<StoreImage>> captor = ArgumentCaptor.forClass(List.class);
            then(storeImageRepository).should(times(1)).saveAll(captor.capture());

            List<StoreImage> saved = captor.getValue();
            assertThat(saved).hasSize(3);
            assertThat(saved.get(0).getMediaId()).isEqualTo(1L);
            assertThat(saved.get(1).getMediaId()).isNull();              // null 그대로
            assertThat(saved.get(2).getMediaId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("모든 이미지의 mediaId가 null이어도 저장된다")
        void save_withAllNullMediaId_allSaved() {
            // given
            givenBaseMocks();
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, null, 0),
                new StoreUpdateRequest.StoreImageRequest(ImageType.BANNER,    null, 1)
            );

            // when
            storeCommandService.update(USER_ID, STORE_ID, requestWithImages(images));

            // then
            ArgumentCaptor<List<StoreImage>> captor = ArgumentCaptor.forClass(List.class);
            then(storeImageRepository).should(times(1)).saveAll(captor.capture());

            List<StoreImage> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            assertThat(saved).allMatch(img -> img.getMediaId() == null);
        }

        @Test
        @DisplayName("images 자체가 null이면 saveAll 호출하지 않는다")
        void save_withNullImages_doesNotCallSaveAll() {
            // given
            givenBaseMocks();

            // when
            storeCommandService.update(USER_ID, STORE_ID, requestWithImages(null));

            // then
            then(storeImageRepository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("이미지 저장 시 imageType과 sortOrder도 정확히 저장된다")
        void save_imageTypeAndSortOrder_savedCorrectly() {
            // given
            givenBaseMocks();
            List<StoreUpdateRequest.StoreImageRequest> images = List.of(
                new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, null, 3)
            );

            // when
            storeCommandService.update(USER_ID, STORE_ID, requestWithImages(images));

            // then
            ArgumentCaptor<List<StoreImage>> captor = ArgumentCaptor.forClass(List.class);
            then(storeImageRepository).should(times(1)).saveAll(captor.capture());

            StoreImage saved = captor.getValue().get(0);
            assertThat(saved.getImageType()).isEqualTo(ImageType.THUMBNAIL);
            assertThat(saved.getSortOrder()).isEqualTo(3);
            assertThat(saved.getMediaId()).isNull();
        }
    }
}
