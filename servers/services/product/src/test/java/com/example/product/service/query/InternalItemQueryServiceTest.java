package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemSearchDocumentResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.service.content.ItemContentService;
import com.example.product.service.query.detail.ItemCategoryDetailResolver;
import com.example.product.service.query.detail.ItemCategoryDetailView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
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
    @Mock
    private ItemContentService itemContentService;
    @Mock
    private ItemCategoryDetailResolver itemCategoryDetailResolver;
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

    @Test
    void findSearchDocuments_withCursorQueriesVisibleItemsInDescendingOrder() {
        Item first = createItem(21L, 501L, ItemStatus.ON_SALE, 300L);
        Item second = createItem(18L, 502L, ItemStatus.FUNDING, 301L);

        when(itemRepository.findByStatusInAndIdLessThanOrderByIdDesc(
                eq(ItemStatus.publiclyVisibleStatuses()),
                eq(30L),
                any(Pageable.class)
        )).thenReturn(List.of(first, second));
        when(itemCategoryDetailResolver.resolve(List.of(first, second))).thenReturn(Map.of(
                21L, new ItemCategoryDetailView(501L, "굿즈", List.of("굿즈")),
                18L, new ItemCategoryDetailView(502L, "공연", List.of("공연"))
        ));
        when(itemContentService.findByItemIds(List.of(21L, 18L))).thenReturn(Map.of(
                21L, new ItemContentSnapshot(List.of("태그1"), List.of("특징1"), List.of()),
                18L, ItemContentSnapshot.empty()
        ));

        CursorResponse<ItemSearchDocumentResponse> response = internalItemQueryService.findSearchDocuments("MzA", 2);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems()).extracting(ItemSearchDocumentResponse::itemId)
                .containsExactly(21L, 18L);
        assertThat(response.getNextCursor()).isNull();
        verify(itemRepository).findByStatusInAndIdLessThanOrderByIdDesc(
                eq(ItemStatus.publiclyVisibleStatuses()),
                eq(30L),
                any(Pageable.class)
        );
    }

    private Item createItem(Long itemId, Long categoryId, ItemStatus status, Long storeId) {
        Item item = Item.create(
                "테스트 상품 " + itemId,
                "desc",
                10_000L,
                ItemType.GOODS,
                categoryId,
                200L,
                storeId,
                400L
        );
        ReflectionTestUtils.setField(item, "id", itemId);
        ReflectionTestUtils.setField(item, "status", status);
        ReflectionTestUtils.setField(item, "updatedAt", LocalDateTime.of(2026, 3, 12, 10, 15));
        return item;
    }
}
