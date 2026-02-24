package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemGoodsLinkRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.command.image.ItemThumbnailSyncService;
import com.example.product.service.command.image.MediaReferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsCommandServiceTest {

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
    private EventPublisher eventPublisher;

    @InjectMocks
    private GoodsCommandService goodsCommandService;

    @Test
    void delete_clearsItemMediaLinks() {
        Long itemId = 2L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        goodsCommandService.delete(itemId, sellerId, 100L);

        verify(itemOptionRepository).softDeleteAllByItemId(itemId);
        verify(shippingInfoRepository).softDeleteByItemId(itemId);
        verify(itemGoodsLinkRepository).softDeleteAllByGoodsItemId(itemId);
        verify(itemImageRepository).softDeleteAllByItemId(itemId);
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, null, true);
        assertThat(item.getThumbnailMediaId()).isNull();
    }

    @Test
    void delete_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 2L;
        Long sellerId = 10L;
        Item productItem = createItem(itemId, sellerId, ItemType.PRODUCT);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(productItem));

        assertThatThrownBy(() -> goodsCommandService.delete(itemId, sellerId, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(itemOptionRepository, shippingInfoRepository, itemGoodsLinkRepository, itemImageRepository);
        verifyNoInteractions(itemThumbnailSyncService);
    }

    @Test
    void delete_whenStoreHeaderMismatch_throwsOwnershipError() {
        Long itemId = 2L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> goodsCommandService.delete(itemId, sellerId, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.STORE_OWNERSHIP_MISMATCH);
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
