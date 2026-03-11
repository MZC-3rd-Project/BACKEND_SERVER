package com.example.store.service.test.command;

import com.example.core.exception.BusinessException;
import com.example.store.dto.request.StoreUpdateRequest;
import com.example.store.dto.response.StoreUpdateResponse;
import com.example.store.entity.*;
import com.example.store.exception.StoreErrorCode;
import com.example.store.repository.*;
import com.example.store.service.command.StoreCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreCommandService - update 단위 테스트")
class StoreCommandServiceUpdateTest {

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
            .storeName("기존 가게")
            .status(StoreStatus.INACTIVE)
            .build();
    }

    private StoreAddress mockAddress() {
        return StoreAddress.builder()
            .store(mockStore())
            .address("서울시 강남구 기존 주소")
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

    private StoreProfile mockProfile() {
        return StoreProfile.builder()
            .store(mockStore())
            .description("기존 소개글")
            .build();
    }

    // 전체 필드 수정 요청
    private StoreUpdateRequest fullRequest() {
        return new StoreUpdateRequest(
            "새 가게 이름",
            StoreStatus.ACTIVE,
            "서울시 서초구 새 주소",
            AddressType.MAIN,
            "새 소개글",
            "010-9999-9999",
            ContactType.PHONE,
            List.of(new StoreUpdateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0))
        );
    }

    // 일부 필드만 수정 요청 (storeName, description만)
    private StoreUpdateRequest partialRequest() {
        return new StoreUpdateRequest(
            "새 가게 이름",
            null,
            null,
            null,
            "새 소개글",
            null,
            null,
            null
        );
    }

    // 공통 given 셋업
    private void givenStoreFound() {
        given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
            .willReturn(Optional.of(mockStore()));
    }

    private void givenAddressFound() {
        given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID))
            .willReturn(Optional.of(mockAddress()));
    }

    private void givenContactFound() {
        given(storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(STORE_ID))
            .willReturn(Optional.of(mockContact()));
    }

    private void givenProfileFound() {
        given(storeProfileRepository.findByStoreId(STORE_ID))
            .willReturn(Optional.of(mockProfile()));
    }

    private void givenProfileNotFound() {
        given(storeProfileRepository.findByStoreId(STORE_ID))
            .willReturn(Optional.empty());
    }

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("정상 수정")
    class Success {

        @Test
        @DisplayName("전체 필드 수정 성공 - 응답 값 검증")
        void update_fullRequest_success() {
            // given
            givenStoreFound();
            givenAddressFound();
            givenContactFound();
            givenProfileFound();

            // when
            StoreUpdateResponse response = storeCommandService.update(USER_ID, STORE_ID, fullRequest());

            // then
            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.description()).isEqualTo("새 소개글");
        }

        @Test
        @DisplayName("이미지 포함 시 storeImageRepository.saveAll() 호출")
        void update_withImages_savesImages() {
            // given
            givenStoreFound();
            givenAddressFound();
            givenContactFound();
            givenProfileFound();

            // when
            storeCommandService.update(USER_ID, STORE_ID, fullRequest());

            // then
            then(storeImageRepository).should(times(1)).saveAll(any());
        }

        @Test
        @DisplayName("images가 null이면 storeImageRepository.saveAll() 미호출")
        void update_withoutImages_doesNotSaveImages() {
            // given
            givenStoreFound();
            givenAddressFound();
            givenContactFound();
            givenProfileFound();

            // when
            storeCommandService.update(USER_ID, STORE_ID, partialRequest());

            // then
            then(storeImageRepository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("프로필이 존재하면 updateDescription() 호출 (새로 save 안 함)")
        void update_profileExists_updatesDescription() {
            // given
            givenStoreFound();
            givenAddressFound();
            givenContactFound();
            givenProfileFound();

            // when
            storeCommandService.update(USER_ID, STORE_ID, fullRequest());

            // then - save 호출 안 됨 (update만)
            then(storeProfileRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("프로필이 없으면 새로 StoreProfile save 호출")
        void update_profileNotExists_savesNewProfile() {
            // given
            givenStoreFound();
            givenAddressFound();
            givenContactFound();
            givenProfileNotFound();

            // when
            storeCommandService.update(USER_ID, STORE_ID, fullRequest());

            // then
            then(storeProfileRepository).should(times(1)).save(any(StoreProfile.class));
        }

        @Test
        @DisplayName("응답에 이미지 목록이 포함된다")
        void update_withImages_responseContainsImages() {
            // given
            givenStoreFound();
            givenAddressFound();
            givenContactFound();
            givenProfileFound();

            // when
            StoreUpdateResponse response = storeCommandService.update(USER_ID, STORE_ID, fullRequest());

            // then
            assertThat(response.images()).hasSize(1);
            assertThat(response.images().get(0).mediaId()).isEqualTo(1L);
        }
    }

    // ── 예외 케이스 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("수정 실패")
    class Fail {

        @Test
        @DisplayName("가게가 없으면 STORE_NOT_FOUND 예외 발생")
        void update_storeNotFound_throwsException() {
            // given
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.update(USER_ID, STORE_ID, fullRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.STORE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("소유자가 다르면 STORE_ACCESS_DENIED 예외 발생")
        void update_differentOwner_throwsException() {
            // given
            Long otherUserId = 999L;
            given(storesRepository.findByIdAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.of(mockStore())); // mockStore의 userId = 1L

            // when & then
            assertThatThrownBy(() -> storeCommandService.update(otherUserId, STORE_ID, fullRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.STORE_ACCESS_DENIED.getMessage());

            // 이후 로직 실행 안 됨
            then(storeAddressRepository).should(never()).findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("주소가 없으면 ADDRESS_NOT_FOUND 예외 발생")
        void update_addressNotFound_throwsException() {
            // given
            givenStoreFound();
            given(storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.update(USER_ID, STORE_ID, fullRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.ADDRESS_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("연락처가 없으면 CONTACT_NOT_FOUND 예외 발생")
        void update_contactNotFound_throwsException() {
            // given
            givenStoreFound();
            givenAddressFound();
            givenProfileFound();
            given(storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(STORE_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> storeCommandService.update(USER_ID, STORE_ID, fullRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(StoreErrorCode.CONTACT_NOT_FOUND.getMessage());
        }
    }
}
