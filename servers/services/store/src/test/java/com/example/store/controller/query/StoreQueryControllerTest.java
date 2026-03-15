package com.example.store.controller.query;

import com.example.api.response.ApiResponse;
import com.example.store.dto.image.StoreImageResponse;
import com.example.store.dto.response.StoreDetailResponse;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.entity.AddressType;
import com.example.store.entity.ImageType;
import com.example.store.entity.StoreStatus;
import com.example.store.service.query.StoreQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreQueryControllerTest {

    @Mock
    private StoreQueryService storeQueryService;

    private StoreQueryController storeQueryController;

    @BeforeEach
    void setUp() {
        storeQueryController = new StoreQueryController(storeQueryService);
    }

    @Test
    void getMyStore_returnsExplicitStoreListResponse() {
        List<StoreListResponse> myStores = List.of(
            new StoreListResponse(
                1L,
                2L,
                "내 스토어",
                StoreStatus.ACTIVE,
                "설명",
                "010-1234-5678",
                "서울",
                StoreImageResponse.builder()
                    .storeId(1L)
                    .mediaId(10L)
                    .imageType(ImageType.THUMBNAIL)
                    .sortOrder(0)
                    .build()
            )
        );
        when(storeQueryService.getMyStoreList(2L)).thenReturn(myStores);

        ApiResponse<List<StoreListResponse>> response = storeQueryController.getMyStore(2L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().storeName()).isEqualTo("내 스토어");
        verify(storeQueryService).getMyStoreList(2L);
    }

    @Test
    void getStoreQueryList_returnsExplicitPageResponse() {
        Page<StoreListResponse> page = new PageImpl<>(
            List.of(
                new StoreListResponse(1L, 2L, "스토어", StoreStatus.ACTIVE, "설명", "010", "서울", null)
            ),
            PageRequest.of(0, 20),
            1
        );
        when(storeQueryService.getStoreListInfo(PageRequest.of(0, 20))).thenReturn(page);

        ApiResponse<Page<StoreListResponse>> response = storeQueryController.getStoreQueryList(PageRequest.of(0, 20));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).hasSize(1);
        verify(storeQueryService).getStoreListInfo(PageRequest.of(0, 20));
    }

    @Test
    void getStoreQueryDetail_returnsExplicitDetailResponse() {
        StoreDetailResponse detailResponse = new StoreDetailResponse(
            1L,
            2L,
            "스토어",
            StoreStatus.ACTIVE,
            "설명",
            "서울",
            AddressType.MAIN,
            null
        );
        when(storeQueryService.getStoreDetail(1L)).thenReturn(detailResponse);

        ApiResponse<StoreDetailResponse> response = storeQueryController.getStoreQueryDetail(1L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().storeName()).isEqualTo("스토어");
        verify(storeQueryService).getStoreDetail(1L);
    }
}
