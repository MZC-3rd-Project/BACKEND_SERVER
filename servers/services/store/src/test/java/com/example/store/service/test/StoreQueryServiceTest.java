package com.example.store.service.test;

import com.example.store.dto.image.StoreImageResponse;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.ImageType;
import com.example.store.entity.StoreImage;
import com.example.store.entity.StoreStatus;
import com.example.store.repository.StoresRepository;
import com.example.store.service.query.StoreQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreQueryService 단위 테스트")
class StoreQueryServiceTest {

    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private StoreQueryService storeQueryService;

    private StoreListResponse storeListResponse() {
        StoreImageResponse thumbnail = StoreImageResponse.builder()
            .storeId(1L)
            .mediaId(1L)
            .imageType(ImageType.THUMBNAIL)
            .sortOrder(0)
            .build();

        return new StoreListResponse(
            1L,
            2L,
            "테스트 가게",
            StoreStatus.ACTIVE,
            "맛있는 음식점입니다.",
            "010-1234-5678",
            "서울시 강남구 테헤란로 1길",
            thumbnail
        );
    }

    private StoreSnapshotResponse storeSnapshotResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new StoreSnapshotResponse(
            1L,
            2L,
            "테스트 가게",
            StoreStatus.ACTIVE,
            "맛있는 음식점입니다.",
            "서울시 강남구 테헤란로 1길",
            AddressType.MAIN,
            "010-1234-5678",
            ContactType.PHONE,
            List.of(),
            now.minusDays(3),
            now.minusHours(2)
        );
    }

    @Nested
    @DisplayName("getStoreListInfo()")
    class GetStoreListInfo {

        @Test
        @DisplayName("정상 조회 - 데이터가 있을 때 Page 반환")
        void success_returns_page() {
            Pageable pageable = PageRequest.of(0, 20);
            List<StoreListResponse> content = List.of(storeListResponse());
            Page<StoreListResponse> expected = new PageImpl<>(content, pageable, 1);

            given(storesRepository.findStoreList(pageable)).willReturn(expected);

            Page<StoreListResponse> result = storeQueryService.getStoreListInfo(pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).storeName()).isEqualTo("테스트 가게");
            assertThat(result.getContent().get(0).status()).isEqualTo(StoreStatus.ACTIVE);
            assertThat(result.getContent().get(0).contactValue()).isEqualTo("010-1234-5678");
            assertThat(result.getContent().get(0).address()).isEqualTo("서울시 강남구 테헤란로 1길");

            then(storesRepository).should(times(1)).findStoreList(pageable);
        }

        @Test
        @DisplayName("정상 조회 - 데이터가 없을 때 빈 Page 반환")
        void success_returns_empty_page() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<StoreListResponse> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            given(storesRepository.findStoreList(pageable)).willReturn(emptyPage);

            Page<StoreListResponse> result = storeQueryService.getStoreListInfo(pageable);

            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.getTotalElements()).isZero();
            then(storesRepository).should(times(1)).findStoreList(pageable);
        }
    }

    @Nested
    @DisplayName("getStoreSnapshot()")
    class GetStoreSnapshot {

        @Test
        @DisplayName("snapshot base 정보와 image 목록을 합쳐 반환한다")
        void success_returns_snapshot_with_images() {
            given(storesRepository.findSnapshotByStoreId(1L))
                .willReturn(Optional.of(storeSnapshotResponse()));
            given(storesRepository.findImagesByStoreId(1L)).willReturn(List.of(
                StoreImage.of(ImageType.THUMBNAIL, 10L, 0),
                StoreImage.of(ImageType.GALLERY, 11L, 1)
            ));

            StoreSnapshotResponse result = storeQueryService.getStoreSnapshot(1L);

            assertThat(result.storeId()).isEqualTo(1L);
            assertThat(result.contactValue()).isEqualTo("010-1234-5678");
            assertThat(result.images()).hasSize(2);
            assertThat(result.images().get(0).mediaId()).isEqualTo(10L);
            then(storesRepository).should(times(1)).findSnapshotByStoreId(1L);
            then(storesRepository).should(times(1)).findImagesByStoreId(1L);
        }
    }

    @Nested
    @DisplayName("getActiveStoreIdsByUserId()")
    class GetActiveStoreIdsByUserId {

        @Test
        @DisplayName("양수 userId면 repository 결과를 반환한다")
        void success_returns_store_ids() {
            given(storesRepository.findIdsByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(2L))
                .willReturn(List.of(101L, 102L));

            List<Long> result = storeQueryService.getActiveStoreIdsByUserId(2L);

            assertThat(result).containsExactly(101L, 102L);
            then(storesRepository).should(times(1)).findIdsByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(2L);
        }

        @Test
        @DisplayName("잘못된 userId면 빈 목록을 반환하고 repository를 호출하지 않는다")
        void invalid_user_id_returns_empty_list() {
            List<Long> result = storeQueryService.getActiveStoreIdsByUserId(0L);

            assertThat(result).isEmpty();
            then(storesRepository).shouldHaveNoInteractions();
        }
    }
}
