package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.product.dto.goods.request.ProductCreateRequest;
import com.example.product.dto.goods.request.ProductUpdateRequest;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void createProduct_withThumbnail_schedulesMediaSync() {
        Long itemId = 100L;
        ProductCreateRequest request = new ProductCreateRequest();
        ReflectionTestUtils.setField(request, "title", "new item");
        ReflectionTestUtils.setField(request, "description", "desc");
        ReflectionTestUtils.setField(request, "price", 1000L);
        ReflectionTestUtils.setField(request, "storeId", 10L);
        ReflectionTestUtils.setField(request, "thumbnailMediaId", 501L);

        when(mediaReferenceService.resolveMediaUrl(501L)).thenReturn("https://media/501");
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", itemId);
            return saved;
        });
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(java.util.List.of());

        productCommandService.createProduct(request, 77L);

        verify(itemMediaLinkSyncService).syncAfterCommit(itemId, 501L, java.util.List.of());
        verify(eventPublisher).publish(any(), any());
    }

    @Test
    void createProduct_whenMediaReferenceInvalid_throwsAndSkipsSave() {
        ProductCreateRequest request = new ProductCreateRequest();
        ReflectionTestUtils.setField(request, "title", "new item");
        ReflectionTestUtils.setField(request, "description", "desc");
        ReflectionTestUtils.setField(request, "price", 1000L);
        ReflectionTestUtils.setField(request, "storeId", 10L);
        ReflectionTestUtils.setField(request, "thumbnailMediaId", 501L);

        when(mediaReferenceService.resolveMediaUrl(501L))
                .thenThrow(new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE));

        assertThatThrownBy(() -> productCommandService.createProduct(request, 77L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_MEDIA_REFERENCE);

        verifyNoInteractions(itemMediaLinkSyncService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateProduct_withThumbnailAndGallery_schedulesMediaSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "updated");
        ReflectionTestUtils.setField(request, "thumbnailMediaId", 888L);

        ItemImage image = ItemImage.create(itemId, 999L, "https://image/999", 0, false);
        ReflectionTestUtils.setField(image, "id", 5000L);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(mediaReferenceService.resolveMediaUrl(888L)).thenReturn("https://media/888");
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(java.util.List.of(image));

        productCommandService.updateProduct(itemId, request, sellerId);

        verify(itemMediaLinkSyncService).syncAfterCommit(itemId, 888L, java.util.List.of(999L));
        verify(eventPublisher).publish(any(), any());
    }

    @Test
    void updateProduct_whenMediaReferenceInvalid_throwsAndSkipsSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "thumbnailMediaId", 777L);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(mediaReferenceService.resolveMediaUrl(777L))
                .thenThrow(new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE));

        assertThatThrownBy(() -> productCommandService.updateProduct(itemId, request, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_MEDIA_REFERENCE);

        verifyNoInteractions(itemMediaLinkSyncService);
    }

    @Test
    void delete_clearsItemMediaLinks() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

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
