package com.example.product.service.command;

import com.example.event.EventPublisher;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
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
class ProductCommandServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemOptionRepository itemOptionRepository;
    @Mock
    private ShippingInfoRepository shippingInfoRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private MediaReferenceService mediaReferenceService;
    @Mock
    private ItemMediaLinkSyncService itemMediaLinkSyncService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ProductCommandService productCommandService;

    @Test
    void delete_clearsItemMediaLinks() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        productCommandService.delete(itemId, sellerId);

        verify(itemOptionRepository).softDeleteAllByItemId(itemId);
        verify(shippingInfoRepository).softDeleteByItemId(itemId);
        verify(itemImageRepository).softDeleteAllByItemId(itemId);
        verify(itemMediaLinkSyncService).clearAfterCommit(itemId);
        assertThat(item.getThumbnailMediaId()).isNull();
    }

    private Item createItem(Long id, Long sellerId) {
        Item item = Item.create(
                "item",
                "desc",
                1000L,
                ItemType.PRODUCT,
                null,
                sellerId,
                100L,
                999L,
                "https://thumbnail"
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
