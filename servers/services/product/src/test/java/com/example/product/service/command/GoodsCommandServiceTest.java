package com.example.product.service.command;

import com.example.event.EventPublisher;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.repository.ItemGoodsLinkRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
    private ItemMediaLinkSyncService itemMediaLinkSyncService;
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

        goodsCommandService.delete(itemId, sellerId);

        verify(itemOptionRepository).softDeleteAllByItemId(itemId);
        verify(shippingInfoRepository).softDeleteByItemId(itemId);
        verify(itemGoodsLinkRepository).softDeleteAllByGoodsItemId(itemId);
        verify(itemImageRepository).softDeleteAllByItemId(itemId);
        verify(itemMediaLinkSyncService).clearAfterCommit(itemId);
        assertThat(item.getThumbnailMediaId()).isNull();
    }

    private Item createItem(Long id, Long sellerId) {
        Item item = Item.create(
                "goods",
                "desc",
                1000L,
                ItemType.GOODS,
                null,
                sellerId,
                100L,
                888L
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
