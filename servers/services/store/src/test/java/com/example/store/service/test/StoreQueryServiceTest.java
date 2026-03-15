package com.example.store.service.test;

import com.example.store.dto.response.StoreListResponse;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.ImageType;
import com.example.store.entity.StoreStatus;
import com.example.store.repository.StoresRepository;
import com.example.store.service.query.StoreDetailAssembler;
import com.example.store.service.query.StoreImageSelectionPolicy;
import com.example.store.service.query.StoreQueryService;
import com.example.store.service.query.view.StoreDetailBaseView;
import com.example.store.service.query.view.StoreImageView;
import com.example.store.service.query.view.StoreListView;
import com.example.store.service.query.view.StoreSnapshotBaseView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private StoreQueryService storeQueryService;

    @BeforeEach
    void setUp() {
        StoreImageSelectionPolicy storeImageSelectionPolicy = new StoreImageSelectionPolicy();
        StoreDetailAssembler storeDetailAssembler = new StoreDetailAssembler(storeImageSelectionPolicy);
        storeQueryService = new StoreQueryService(storesRepository, storeDetailAssembler);
    }

    private StoreListView storeListView() {
        return new StoreListView(
            1L,
            2L,
            "테스트 가게",
            StoreStatus.ACTIVE,
            "맛있는 음식점입니다.",
            "서울시 강남구 테헤란로 1길",
            "010-1234-5678",
            11L,
            1L,
            ImageType.THUMBNAIL,
            0
        );
    }

    private StoreDetailBaseView storeDetailBaseView() {
        return new StoreDetailBaseView(
            1L,
            2L,
            "테스트 가게",
            StoreStatus.ACTIVE,
            "맛있는 음식점입니다.",
            "서울시 강남구 테헤란로 1길",
            AddressType.MAIN
        );
    }

    private StoreSnapshotBaseView storeSnapshotBaseView() {
        LocalDateTime now = LocalDateTime.now();
        return new StoreSnapshotBaseView(
            1L,
            2L,
            "테스트 가게",
            StoreStatus.ACTIVE,
            "맛있는 음식점입니다.",
            "서울시 강남구 테헤란로 1길",
            AddressType.MAIN,
            "010-1234-5678",
            ContactType.PHONE,
            now.minusDays(3),
            now.minusHours(2)
        );
    }

    private List<StoreImageView> storeImageViews() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
            new StoreImageView(11L, 1L, 10L, ImageType.THUMBNAIL, 0, now.minusHours(1)),
            new StoreImageView(12L, 1L, 11L, ImageType.GALLERY, 1, now)
        );
    }

    @Nested
    @DisplayName("getStoreListInfo()")
    class GetStoreListInfo {

        @Test
        @DisplayName("정상 조회 - 데이터가 있을 때 Page 반환")
        void success_returns_page() {
            Pageable pageable = PageRequest.of(0, 20);
            List<StoreListView> content = List.of(storeListView());
            Page<StoreListView> expected = new PageImpl<>(content, pageable, 1);

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
            Page<StoreListView> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            given(storesRepository.findStoreList(pageable)).willReturn(emptyPage);

            Page<StoreListResponse> result = storeQueryService.getStoreListInfo(pageable);

            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.getTotalElements()).isZero();
            then(storesRepository).should(times(1)).findStoreList(pageable);
        }
    }

    @Nested
    @DisplayName("getMyStoreList()")
    class GetMyStoreList {

        @Test
        @DisplayName("양수 userId면 내 store 목록을 명시 DTO로 반환한다")
        void success_returns_my_store_list() {
            given(storesRepository.findStoreListByUserId(2L))
                .willReturn(List.of(storeListView()));

            List<StoreListResponse> result = storeQueryService.getMyStoreList(2L);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().userId()).isEqualTo(2L);
            assertThat(result.getFirst().storeName()).isEqualTo("테스트 가게");
            then(storesRepository).should(times(1)).findStoreListByUserId(2L);
        }

        @Test
        @DisplayName("잘못된 userId면 빈 목록을 반환하고 repository를 호출하지 않는다")
        void invalid_user_id_returns_empty_list() {
            List<StoreListResponse> result = storeQueryService.getMyStoreList(0L);

            assertThat(result).isEmpty();
            then(storesRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("getStoreDetail()")
    class GetStoreDetail {

        @Test
        @DisplayName("base projection과 image projection을 조합해 상세 응답을 반환한다")
        void success_returns_detail_response() {
            given(storesRepository.findDetailBaseByStoreId(1L))
                .willReturn(Optional.of(storeDetailBaseView()));
            given(storesRepository.findImagesByStoreId(1L)).willReturn(storeImageViews());

            var result = storeQueryService.getStoreDetail(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.image()).isNotNull();
            assertThat(result.image().getThumbnail()).isNotNull();
            assertThat(result.image().getThumbnail().getStoreId()).isEqualTo(1L);
            assertThat(result.image().getThumbnail().getMediaId()).isEqualTo(10L);
            assertThat(result.image().getGallery()).hasSize(1);
            assertThat(result.image().getGallery().getFirst().getMediaId()).isEqualTo(11L);
            then(storesRepository).should(times(1)).findDetailBaseByStoreId(1L);
            then(storesRepository).should(times(1)).findImagesByStoreId(1L);
        }
    }

    @Nested
    @DisplayName("getStoreSnapshot()")
    class GetStoreSnapshot {

        @Test
        @DisplayName("snapshot base 정보와 image 목록을 합쳐 반환한다")
        void success_returns_snapshot_with_images() {
            given(storesRepository.findSnapshotBaseByStoreId(1L))
                .willReturn(Optional.of(storeSnapshotBaseView()));
            given(storesRepository.findImagesByStoreId(1L)).willReturn(storeImageViews());

            StoreSnapshotResponse result = storeQueryService.getStoreSnapshot(1L);

            assertThat(result.storeId()).isEqualTo(1L);
            assertThat(result.contactValue()).isEqualTo("010-1234-5678");
            assertThat(result.images()).hasSize(2);
            assertThat(result.images().get(0).mediaId()).isEqualTo(10L);
            then(storesRepository).should(times(1)).findSnapshotBaseByStoreId(1L);
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
