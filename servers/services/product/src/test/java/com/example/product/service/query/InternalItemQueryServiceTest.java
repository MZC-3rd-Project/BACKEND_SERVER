package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalItemQueryServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Spy
    private ItemAccessPolicy itemAccessPolicy = new ItemAccessPolicy();

    @InjectMocks
    private InternalItemQueryService internalItemQueryService;

    @Test
    void findById_whenItemMissing_throwsItemNotFound() {
        Long itemId = 1L;
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> internalItemQueryService.findById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void findItemsEndingSoon_queriesOnSaleCandidates() {
        when(itemRepository.findByStatusIn(eq(List.of(ItemStatus.ON_SALE)), any(Pageable.class)))
                .thenReturn(List.of());
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of()))
                .thenReturn(List.of());

        internalItemQueryService.findItemsEndingSoon();

        verify(itemRepository).findByStatusIn(eq(List.of(ItemStatus.ON_SALE)), any(Pageable.class));
    }

    @Test
    void findByStoreId_queriesVisibleStatusesAndMapsSummaries() {
        Item item = Item.create(
            "테스트 상품",
            "desc",
            10_000L,
            ItemType.GOODS,
            1L,
            200L,
            300L,
            400L
        );
        ReflectionTestUtils.setField(item, "id", 10L);
        ReflectionTestUtils.setField(item, "status", ItemStatus.ON_SALE);
        ReflectionTestUtils.setField(item, "updatedAt", LocalDateTime.of(2026, 3, 12, 10, 15));

        when(itemRepository.findByStoreIdAndStatusInOrderByUpdatedAtDesc(
            eq(300L),
            eq(ItemStatus.publiclyVisibleStatuses())
        )).thenReturn(List.of(item));

        var result = internalItemQueryService.findByStoreId(300L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).itemId()).isEqualTo(10L);
        assertThat(result.get(0).storeId()).isEqualTo(300L);
        assertThat(result.get(0).thumbnailMediaId()).isEqualTo(400L);
        assertThat(result.get(0).sourceUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 12, 10, 15));
        verify(itemRepository).findByStoreIdAndStatusInOrderByUpdatedAtDesc(
            eq(300L),
            eq(ItemStatus.publiclyVisibleStatuses())
        );
    }
}
