package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.product.dto.goods.request.GoodsCreateRequest;
import com.example.product.dto.goods.request.GoodsUpdateRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.entity.goods.ItemGoodsLink;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.event.ItemDeletedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.ItemGoodsLinkRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.command.image.ItemThumbnailSyncService;
import com.example.product.service.command.image.MediaReferenceService;
import com.example.product.service.content.ItemContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsCommandServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemOptionRepository itemOptionRepository;
    @Mock
    private ShippingInfoRepository shippingInfoRepository;
    @Mock
    private ItemGoodsLinkRepository itemGoodsLinkRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private MediaReferenceService mediaReferenceService;
    @Mock
    private ItemThumbnailSyncService itemThumbnailSyncService;
    @Mock
    private StoreOwnershipValidator storeOwnershipValidator;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ItemContentService itemContentService;

    @InjectMocks
    private GoodsCommandService goodsCommandService;

    @Test
    void delete_clearsItemMediaLinks() {
        Long itemId = 2L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        goodsCommandService.delete(itemId, sellerId);

        verify(itemOptionRepository).softDeleteAllByItemId(itemId);
        verify(shippingInfoRepository).softDeleteByItemId(itemId);
        verify(itemGoodsLinkRepository).softDeleteAllByGoodsItemId(itemId);
        verify(itemImageRepository).softDeleteAllByItemId(itemId);
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, null, true);
        verify(eventPublisher).publish(any(ItemDeletedEvent.class), any());
        assertThat(item.getThumbnailMediaId()).isNull();
    }

    @Test
    void delete_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 2L;
        Long sellerId = 10L;
        Item productItem = createItem(itemId, sellerId, ItemType.PRODUCT);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(productItem));

        assertThatThrownBy(() -> goodsCommandService.delete(itemId, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(itemOptionRepository, shippingInfoRepository, itemGoodsLinkRepository, itemImageRepository);
        verifyNoInteractions(itemThumbnailSyncService);
    }

    @Test
    void createGoods_whenCategoryNotFound_throwsBusinessError() {
        GoodsCreateRequest request = new GoodsCreateRequest();
        ReflectionTestUtils.setField(request, "title", "goods");
        ReflectionTestUtils.setField(request, "description", "desc");
        ReflectionTestUtils.setField(request, "price", 1000L);
        ReflectionTestUtils.setField(request, "storeId", 1L);
        ReflectionTestUtils.setField(request, "categoryId", 999L);

        when(categoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> goodsCommandService.createGoods(request, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);

        verifyNoInteractions(itemRepository, itemThumbnailSyncService, eventPublisher);
    }

    @Test
    void updateGoods_whenPartialRequest_returnsCurrentSnapshot() {
        Long itemId = 20L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        GoodsUpdateRequest request = new GoodsUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "title-only-update");

        ItemOption option = ItemOption.create(itemId, "옵션A", 0L, 7);
        ShippingInfo shippingInfo = ShippingInfo.create(itemId, 2500L, null, 3, "policy");
        ItemGoodsLink link = ItemGoodsLink.create(41L, itemId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(itemOptionRepository.findByItemId(itemId)).thenReturn(List.of(option));
        when(shippingInfoRepository.findByItemId(itemId)).thenReturn(Optional.of(shippingInfo));
        when(itemGoodsLinkRepository.findByGoodsItemId(itemId)).thenReturn(List.of(link));
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of());

        GoodsDetailResponse response = goodsCommandService.updateGoods(itemId, request, sellerId);

        assertThat(response.getOptions()).hasSize(1);
        assertThat(response.getOptions().get(0).getOptionName()).isEqualTo("옵션A");
        assertThat(response.getShippingInfo()).isNotNull();
        assertThat(response.getShippingInfo().getShippingFee()).isEqualTo(2500L);
        assertThat(response.getLinkedPerformanceItemIds()).containsExactly(41L);
    }

    private Item createItem(Long id, Long sellerId) {
        return createItem(id, sellerId, ItemType.GOODS);
    }

    private Item createItem(Long id, Long sellerId, ItemType itemType) {
        Item item = Item.create(
                "goods",
                "desc",
                1000L,
                itemType,
                null,
                sellerId,
                100L,
                888L
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
