package com.example.store.service.test.command;


import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.response.StoreCreateResponse;
import com.example.store.entity.*;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreCommandService - create 단위 테스트")
class StoreCommandServiceCreateTest {

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

    private final Long USER_ID = 1L;

    private StoreCreateRequest baseRequest() {
        return new StoreCreateRequest(
            "테스트 가게",
            "서울시 강남구 테헤란로 1길",
            AddressType.MAIN,
            "010-1234-5678",
            ContactType.PHONE,
            null,   // description
            null    // images
        );
    }

    private StoreCreateRequest requestWithDescription() {
        return new StoreCreateRequest(
            "테스트 가게",
            "서울시 강남구 테헤란로 1길",
            AddressType.MAIN,
            "010-1234-5678",
            ContactType.PHONE,
            "맛있는 음식점입니다",
            null
        );
    }

    private StoreCreateRequest requestWithImages() {
        return new StoreCreateRequest(
            "테스트 가게",
            "서울시 강남구 테헤란로 1길",
            AddressType.MAIN,
            "010-1234-5678",
            ContactType.PHONE,
            null,
            List.of(
                new StoreCreateRequest.StoreImageRequest(ImageType.THUMBNAIL, 1L, 0),
                new StoreCreateRequest.StoreImageRequest(ImageType.GALLERY,    2L, 1)
            )
        );
    }

    private Stores mockStore() {
        return Stores.builder()
            .id(100L)
            .userId(USER_ID)
            .storeName("테스트 가게")
            .status(StoreStatus.INACTIVE)
            .build();
    }

    private void givenCreatePersistence(Stores savedStore) {
        lenient().when(storeAddressRepository.save(any(StoreAddress.class)))
            .thenReturn(StoreAddress.of(savedStore, AddressType.MAIN, "서울시 강남구 테헤란로 1길"));
        lenient().when(storeContactRepository.save(any(StoreContact.class)))
            .thenReturn(StoreContact.of(savedStore, ContactType.PHONE, "010-1234-5678", true));
        lenient().when(storeProfileRepository.save(any(StoreProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(storeImageRepository.saveAll(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("정상 생성")
    class Success {

        @Test
        @DisplayName("필수 필드만으로 가게 생성 성공")
        void createStore_withRequiredFields_success() {
            // given
            Stores savedStore = mockStore();
            given(storesRepository.existsByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(false);
            given(storesRepository.save(any(Stores.class))).willReturn(savedStore);
            givenCreatePersistence(savedStore);

            // when
            StoreCreateResponse response = storeCommandService.create(USER_ID, baseRequest());
            // then
            assertThat(response).isNotNull();
            assertThat(response.getStoreName()).isEqualTo("테스트 가게");
            assertThat(response.getStatus()).isEqualTo(StoreStatus.ACTIVE);

            // 저장 호출 검증
            then(storesRepository).should(times(1)).save(any(Stores.class));
            then(storeAddressRepository).should(times(1)).save(any(StoreAddress.class));
            then(storeContactRepository).should(times(1)).save(any(StoreContact.class));

            // description, image는 저장 안 함
            then(storeProfileRepository).should(never()).save(any());
            then(storeImageRepository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("description 포함 시 StoreProfile 저장")
        void createStore_withDescription_savesProfile() {
            // given
            Stores savedStore = mockStore();
            given(storesRepository.existsByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(false);
            given(storesRepository.save(any(Stores.class))).willReturn(savedStore);
            givenCreatePersistence(savedStore);

            // when
            storeCommandService.create(USER_ID, requestWithDescription());

            // then
            then(storeProfileRepository).should(times(1)).save(any(StoreProfile.class));
        }

        @Test
        @DisplayName("이미지 포함 시 StoreImage 저장")
        void createStore_withImages_savesImages() {
            // given
            Stores savedStore = mockStore();
            given(storesRepository.existsByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(false);
            given(storesRepository.save(any(Stores.class))).willReturn(savedStore);
            givenCreatePersistence(savedStore);

            // when
            storeCommandService.create(USER_ID, requestWithImages());

            // then
            then(storeImageRepository).should(times(1)).saveAll(any());
        }

        @Test
        @DisplayName("생성된 가게의 초기 status는 ACTIVE")
        void createStore_initialStatus_isActive() {
            // given
            Stores savedStore = Stores.builder()
                .id(100L)
                .userId(USER_ID)
                .storeName("테스트 가게")
                .status(StoreStatus.INACTIVE)
                .build();

            given(storesRepository.existsByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(false);
            given(storesRepository.save(any(Stores.class))).willReturn(savedStore);
            givenCreatePersistence(savedStore);

            // when
            StoreCreateResponse response = storeCommandService.create(USER_ID, baseRequest());

            // then
            assertThat(response.getStatus()).isEqualTo(StoreStatus.ACTIVE);
        }

        @Test
        @DisplayName("생성 시 userId가 응답에 포함된다")
        void createStore_response_containsUserId() {
            // given
            Stores savedStore = Stores.builder()
                .id(100L)
                .userId(USER_ID)
                .storeName("테스트 가게")
                .status(StoreStatus.INACTIVE)
                .build();

            given(storesRepository.existsByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(false);
            given(storesRepository.save(any(Stores.class))).willReturn(savedStore);
            givenCreatePersistence(savedStore);

            // when
            StoreCreateResponse response = storeCommandService.create(USER_ID, baseRequest());

            // then
            assertThat(response.getUserId()).isEqualTo(USER_ID);
        }
    }

    // ── 예외 케이스 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("생성 실패")
    class Fail {

        @Test
        @DisplayName("이미 가게가 존재하면 BusinessException 발생")
        void createStore_alreadyExists_throwsException() {
            // given
            given(storesRepository.existsByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> storeCommandService.create(USER_ID, baseRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 등록된 가게입니다");

            // 저장 로직 호출 안 됨
            then(storesRepository).should(never()).save(any());
            then(storeAddressRepository).should(never()).save(any());
            then(storeContactRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("중복 체크 후 저장이 진행되지 않는다")
        void createStore_duplicateCheck_blocksSave() {
            // given
            given(storesRepository.existsByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(true);

            // when
            assertThatThrownBy(() -> storeCommandService.create(USER_ID, baseRequest()))
                .isInstanceOf(BusinessException.class);

            // then - 어떤 repository도 save 호출 안 됨
            then(storeProfileRepository).should(never()).save(any());
            then(storeImageRepository).should(never()).saveAll(any());
        }
    }
}
