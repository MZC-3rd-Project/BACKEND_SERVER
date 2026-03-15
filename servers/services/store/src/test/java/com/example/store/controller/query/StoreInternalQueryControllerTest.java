package com.example.store.controller.query;

import com.example.api.response.ApiResponse;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.StoreStatus;
import com.example.store.service.query.StoreQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreInternalQueryControllerTest {

    @Mock
    private StoreQueryService storeQueryService;

    private StoreInternalQueryController storeInternalQueryController;

    @BeforeEach
    void setUp() {
        storeInternalQueryController = new StoreInternalQueryController(storeQueryService);
    }

    @Test
    void getStoreSnapshot_delegatesToQueryService() {
        StoreSnapshotResponse snapshotResponse = new StoreSnapshotResponse(
            1L,
            2L,
            "스토어",
            StoreStatus.ACTIVE,
            "설명",
            "서울",
            AddressType.MAIN,
            "010-1234-5678",
            ContactType.PHONE,
            List.of(),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now()
        );
        when(storeQueryService.getStoreSnapshot(1L)).thenReturn(snapshotResponse);

        ApiResponse<StoreSnapshotResponse> response = storeInternalQueryController.getStoreSnapshot(1L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().storeId()).isEqualTo(1L);
        verify(storeQueryService).getStoreSnapshot(1L);
    }

    @Test
    void getActiveStoreIdsByUserId_delegatesToQueryService() {
        when(storeQueryService.getActiveStoreIdsByUserId(2L)).thenReturn(List.of(101L, 102L));

        ApiResponse<List<Long>> response = storeInternalQueryController.getActiveStoreIdsByUserId(2L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).containsExactly(101L, 102L);
        verify(storeQueryService).getActiveStoreIdsByUserId(2L);
    }
}
