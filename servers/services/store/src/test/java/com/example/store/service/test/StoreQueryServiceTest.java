package com.example.store.service.test;

import com.example.store.dto.image.StoreImageResponse;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.entity.ImageType;
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

import java.util.Collections;
import java.util.List;

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

    // ── 픽스처 ─────────────────────────────────────────────────────────────────

    private StoreListResponse storeListResponse() {
        StoreImageResponse thumbnail = StoreImageResponse.builder()
            .storeId(1L)
            .mediaId(1L)
            .imageType(ImageType.THUMBNAIL)
            .sortOrder(0)
            .build();

        return new StoreListResponse(
            1L,                          // id
            2L,                          // userId
            "테스트 가게",                // storeName
            StoreStatus.ACTIVE,          // status
            "맛있는 음식점입니다.",        // description
            "010-1234-5678",             // contactValue
            "서울시 강남구 테헤란로 1길", // address
            thumbnail                    // isThumbnail
        );
    }

    // ── getStoreListInfo ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStoreListInfo()")
    class GetStoreListInfo {

        @Test
        @DisplayName("정상 조회 - 데이터가 있을 때 Page 반환")
        void success_returns_page() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            List<StoreListResponse> content = List.of(storeListResponse());
            Page<StoreListResponse> expected = new PageImpl<>(content, pageable, 1);

            given(storesRepository.findStoreList(pageable)).willReturn(expected);

            // when
            Page<StoreListResponse> result = storeQueryService.getStoreListInfo(pageable);

            // then
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
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<StoreListResponse> emptyPage = new PageImpl<>(
                Collections.emptyList(), pageable, 0
            );

            given(storesRepository.findStoreList(pageable)).willReturn(emptyPage);

            // when
            Page<StoreListResponse> result = storeQueryService.getStoreListInfo(pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.getTotalElements()).isZero();

            then(storesRepository).should(times(1)).findStoreList(pageable);
        }

        @Test
        @DisplayName("페이지 정보 검증 - page, size가 결과에 올바르게 반영된다")
        void success_page_metadata_is_correct() {
            // given
            Pageable pageable = PageRequest.of(1, 5);
            List<StoreListResponse> content = List.of(
                storeListResponse(), storeListResponse()
            );
            Page<StoreListResponse> page = new PageImpl<>(content, pageable, 12L);

            given(storesRepository.findStoreList(pageable)).willReturn(page);

            // when
            Page<StoreListResponse> result = storeQueryService.getStoreListInfo(pageable);

            // then
            assertThat(result.getNumber()).isEqualTo(1);         // 현재 페이지
            assertThat(result.getSize()).isEqualTo(5);           // 페이지 크기
            assertThat(result.getTotalElements()).isEqualTo(12); // 전체 건수
            assertThat(result.getTotalPages()).isEqualTo(3);     // ceil(12/5) = 3
        }

        @Test
        @DisplayName("Repository 호출 횟수 검증 - 서비스 호출 횟수만큼 repository도 호출된다")
        void repository_called_same_times_as_service() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            given(storesRepository.findStoreList(pageable))
                .willReturn(Page.empty(pageable));

            // when
            storeQueryService.getStoreListInfo(pageable);
            storeQueryService.getStoreListInfo(pageable);

            // then
            then(storesRepository).should(times(2)).findStoreList(pageable);
        }
    }
}
