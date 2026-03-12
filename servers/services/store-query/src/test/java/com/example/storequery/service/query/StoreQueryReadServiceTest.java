package com.example.storequery.service.query;

import com.example.core.exception.BusinessException;
import com.example.storequery.dto.response.StoreQueryDetailResponse;
import com.example.storequery.dto.response.StoreQueryListResponse;
import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadItem;
import com.example.storequery.entity.StoreReadModel;
import com.example.storequery.repository.StoreReadImageRepository;
import com.example.storequery.repository.StoreReadItemRepository;
import com.example.storequery.repository.StoreReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreQueryReadServiceTest {

    @Mock
    private StoreReadModelRepository storeReadModelRepository;

    @Mock
    private StoreReadImageRepository storeReadImageRepository;

    @Mock
    private StoreReadItemRepository storeReadItemRepository;

    private StoreQueryReadService service;

    @BeforeEach
    void setUp() {
        service = new StoreQueryReadService(
            storeReadModelRepository,
            storeReadImageRepository,
            storeReadItemRepository
        );
    }

    @Test
    void keyword가_있으면_search_repository를_사용한다() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<StoreReadModel> page = new PageImpl<>(List.of(model()), pageable, 1);
        when(storeReadModelRepository.search(eq("mzc"), eq("ACTIVE"), eq(0.2d), eq(pageable)))
            .thenReturn(page);

        Page<StoreQueryListResponse> result = service.getStores("mzc", StoreQueryStatus.ACTIVE, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(storeReadModelRepository).search("mzc", "ACTIVE", 0.2d, pageable);
    }

    @Test
    void detail은_메인row와_images_items를_합쳐_응답한다() {
        when(storeReadModelRepository.findByStoreIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(model()));
        when(storeReadImageRepository.findByStoreIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).thenReturn(List.of(
            StoreReadImage.of(1L, StoreQueryImageType.THUMBNAIL, 10L, "https://thumb", 0, LocalDateTime.now(), LocalDateTime.now()),
            StoreReadImage.of(1L, StoreQueryImageType.GALLERY, 11L, "https://gallery", 1, LocalDateTime.now(), LocalDateTime.now())
        ));
        when(storeReadItemRepository.findByStoreIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(1L)).thenReturn(List.of(
            StoreReadItem.of(1000L, 1L, 100L, "item1", 1000L, "GOODS", "ON_SALE", 21L, "https://i1", LocalDateTime.now(), LocalDateTime.now())
        ));

        StoreQueryDetailResponse result = service.getStoreDetail(1L);

        assertThat(result.storeId()).isEqualTo(1L);
        assertThat(result.images().thumbnail()).isNotNull();
        assertThat(result.images().gallery()).hasSize(1);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void detail이_없으면_not_found를_던진다() {
        when(storeReadModelRepository.findByStoreIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStoreDetail(99L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void storesByUserId는_userId기준으로_목록을_반환한다() {
        when(storeReadModelRepository.findByUserIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(100L))
            .thenReturn(List.of(model()));

        List<StoreQueryListResponse> result = service.getStoresByUserId(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).storeId()).isEqualTo(1L);
    }

    private static StoreReadModel model() {
        LocalDateTime now = LocalDateTime.now();
        return StoreReadModel.of(
            1L,
            100L,
            "MZC Store",
            "owner",
            "https://owner",
            StoreQueryStatus.ACTIVE,
            "desc",
            "Seoul",
            null,
            "010-1234-5678",
            null,
            10L,
            "https://thumb",
            0,
            1,
            2,
            now,
            "store owner seoul",
            now.minusDays(3),
            now.minusHours(1),
            now
        );
    }
}
