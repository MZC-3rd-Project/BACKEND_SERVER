package com.example.store.service.test.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.store.dto.response.StoreDeleteResponse;
import com.example.store.entity.*;
import com.example.store.exception.StoreErrorCode;
import com.example.store.repository.*;
import com.example.store.service.command.StoreCommandService;
import com.example.store.service.command.StoreMediaReferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreCommandService - delete 단위 테스트")
class StoreCommandServiceDeleteTest {

    @Mock private StoresRepository       storesRepository;
    @Mock private StoreProfileRepository storeProfileRepository;
    @Mock private StoreAddressRepository storeAddressRepository;
    @Mock private StoreContactRepository storeContactRepository;
    @Mock private StoreImageRepository   storeImageRepository;
    @Mock private EventPublisher         eventPublisher;
    @Mock private StoreMediaReferenceService storeMediaReferenceService;

    @InjectMocks
    private StoreCommandService storeCommandService;

    // ── 픽스처 ─────────────────────────────────────────────────────────────────

    private final Long USER_ID  = 1L;
    private final Long STORE_ID = 100L;

    // spy 대신 실제 엔티티 객체 사용 → softDelete() 호출 후 deletedAt 직접 검증
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
            .contactValue("010-1234-5678")
            .contactType(ContactType.PHONE)
            .isPrimary(true)
            .build();
    }

    private StoreImage mockImage() {
        return StoreImage.builder()
            .store(mockStore())
            .imageType(ImageType.THUMBNAIL)
            .mediaId(1L)
            .sortOrder(0)
            .build();
    }

    private StoreProfile mockProfile() {
        return StoreProfile.builder()
            .store(mockStore())
            .description("소개글")
            .build();
    }

    // 모든 연관 엔티티 정상 조회 셋업
    private void givenAllFound(Stores store, StoreAddress address,
                               StoreContact contact, StoreImage image, StoreProfile profile) {
        given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.of(store));
        given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.of(address));
        given(storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.of(contact));
        given(storeImageRepository.findByStoreId(STORE_ID)).willReturn(Optional.of(image));
        given(storeProfileRepository.findByStoreId(STORE_ID)).willReturn(Optional.of(profile));
    }

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("정상 삭제")
    class Success {

        @Test
        @DisplayName("삭제 성공 - 응답에 storeId 포함")
        void delete_success_responseContainsStoreId() {
            // given
            Stores store     = mockStore();
            StoreAddress address = mockAddress();
            StoreContact contact = mockContact();
            StoreImage image     = mockImage();
            StoreProfile profile = mockProfile();
            givenAllFound(store, address, contact, image, profile);

            // when
            StoreDeleteResponse response = storeCommandService.delete(USER_ID, STORE_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(STORE_ID);
        }

        @Test
        @DisplayName("삭제 성공 - Stores softDelete() 호출 후 deletedAt 설정됨")
        void delete_stores_deletedAtIsSet() {
            // given
            Stores store     = mockStore();
            StoreAddress address = mockAddress();
            StoreContact contact = mockContact();
            StoreImage image     = mockImage();
            StoreProfile profile = mockProfile();
            givenAllFound(store, address, contact, image, profile);

            // when
            storeCommandService.delete(USER_ID, STORE_ID);

            // then
            assertThat(store.isDeleted()).isTrue();
            assertThat(store.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("삭제 성공 - StoreAddress softDelete() 호출 후 deletedAt 설정됨")
        void delete_storeAddress_deletedAtIsSet() {
            // given
            Stores store         = mockStore();
            StoreAddress address = mockAddress();
            StoreContact contact = mockContact();
            StoreImage image     = mockImage();
            StoreProfile profile = mockProfile();
            givenAllFound(store, address, contact, image, profile);

            // when
            storeCommandService.delete(USER_ID, STORE_ID);

            // then
            assertThat(address.isDeleted()).isTrue();
            assertThat(address.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("삭제 성공 - StoreContact softDelete() 호출 후 deletedAt 설정됨")
        void delete_storeContact_deletedAtIsSet() {
            // given
            Stores store         = mockStore();
            StoreAddress address = mockAddress();
            StoreContact contact = mockContact();
            StoreImage image     = mockImage();
            StoreProfile profile = mockProfile();
            givenAllFound(store, address, contact, image, profile);

            // when
            storeCommandService.delete(USER_ID, STORE_ID);

            // then
            assertThat(contact.isDeleted()).isTrue();
            assertThat(contact.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("삭제 성공 - StoreImage softDelete() 호출 후 deletedAt 설정됨")
        void delete_storeImage_deletedAtIsSet() {
            // given
            Stores store         = mockStore();
            StoreAddress address = mockAddress();
            StoreContact contact = mockContact();
            StoreImage image     = mockImage();
            StoreProfile profile = mockProfile();
            givenAllFound(store, address, contact, image, profile);

            // when
            storeCommandService.delete(USER_ID, STORE_ID);

            // then
            assertThat(image.isDeleted()).isTrue();
            assertThat(image.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("삭제 성공 - StoreProfile softDelete() 호출 후 deletedAt 설정됨")
        void delete_storeProfile_deletedAtIsSet() {
            // given
            Stores store         = mockStore();
            StoreAddress address = mockAddress();
            StoreContact contact = mockContact();
            StoreImage image     = mockImage();
            StoreProfile profile = mockProfile();
            givenAllFound(store, address, contact, image, profile);

            // when
            storeCommandService.delete(USER_ID, STORE_ID);

            // then
            assertThat(profile.isDeleted()).isTrue();
            assertThat(profile.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("삭제 성공 - 모든 연관 엔티티가 동시에 softDelete 된다")
        void delete_allEntities_softDeletedTogether() {
            // given
            Stores store         = mockStore();
            StoreAddress address = mockAddress();
            StoreContact contact = mockContact();
            StoreImage image     = mockImage();
            StoreProfile profile = mockProfile();
            givenAllFound(store, address, contact, image, profile);

            // when
            storeCommandService.delete(USER_ID, STORE_ID);

            // then - 5개 엔티티 모두 삭제 처리
            assertThat(store.isDeleted()).isTrue();
            assertThat(address.isDeleted()).isTrue();
            assertThat(contact.isDeleted()).isTrue();
            assertThat(image.isDeleted()).isTrue();
            assertThat(profile.isDeleted()).isTrue();
        }
    }

    // ── 예외 케이스 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("삭제 실패")
    class Fail {

        @Test
        @DisplayName("가게가 없으면 STORE_NOT_FOUND 예외 발생")
        void delete_storeNotFound_throwsException() {
            // given
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.delete(USER_ID, STORE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.STORE_NOT_FOUND.getMessage());

            // 이후 조회 로직 실행 안 됨
            then(storeAddressRepository).should(never()).findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(any());
            then(storeContactRepository).should(never()).findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(any());
            then(storeImageRepository).should(never()).findByStoreId(any());
            then(storeProfileRepository).should(never()).findByStoreId(any());
        }

        @Test
        @DisplayName("소유자가 다르면 STORE_ACCESS_DENIED 예외 발생")
        void delete_differentOwner_throwsException() {
            // given
            Long otherUserId = 999L;
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockStore())); // userId = 1L

            // when & then
            assertThatThrownBy(() -> storeCommandService.delete(otherUserId, STORE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.STORE_ACCESS_DENIED.getMessage());

            // 연관 엔티티 조회 실행 안 됨
            then(storeAddressRepository).should(never()).findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("주소가 없으면 ADDRESS_NOT_FOUND 예외 발생")
        void delete_addressNotFound_throwsException() {
            // given
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockStore()));
            given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.delete(USER_ID, STORE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.ADDRESS_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("연락처가 없으면 CONTACT_NOT_FOUND 예외 발생")
        void delete_contactNotFound_throwsException() {
            // given
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockStore()));
            given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockAddress()));
            given(storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.delete(USER_ID, STORE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.CONTACT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("이미지가 없으면 IMAGE_NOT_FOUND 예외 발생")
        void delete_imageNotFound_throwsException() {
            // given
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockStore()));
            given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockAddress()));
            given(storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockContact()));
            given(storeImageRepository.findByStoreId(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.delete(USER_ID, STORE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.IMAGE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("프로필이 없으면 PROFILE_NOT_FOUND 예외 발생")
        void delete_profileNotFound_throwsException() {
            // given
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockStore()));
            given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockAddress()));
            given(storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockContact()));
            given(storeImageRepository.findByStoreId(STORE_ID))
                .willReturn(Optional.of(mockImage()));
            given(storeProfileRepository.findByStoreId(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.delete(USER_ID, STORE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.PROFILE_NOT_FOUND.getMessage());
        }
    }
}
