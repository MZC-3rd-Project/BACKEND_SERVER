package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.product.dto.goods.request.ProductCreateRequest;
import com.example.product.dto.goods.request.ProductUpdateRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
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

import java.util.List;
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
    private CategoryRepository categoryRepository;
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
    private ItemThumbnailSyncService itemThumbnailSyncService;
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

        verify(itemThumbnailSyncService).syncAfterCommit(itemId, 501L);
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

        verifyNoInteractions(itemThumbnailSyncService);
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

        ItemImage image = ItemImage.create(itemId, 999L, 0, false);
        ReflectionTestUtils.setField(image, "id", 5000L);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(mediaReferenceService.resolveMediaUrl(888L)).thenReturn("https://media/888");
        when(itemOptionRepository.findByItemId(itemId)).thenReturn(List.of());
        when(shippingInfoRepository.findByItemId(itemId)).thenReturn(Optional.empty());
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(java.util.List.of(image));

        productCommandService.updateProduct(itemId, request, sellerId);

        verify(itemThumbnailSyncService).syncAfterCommit(itemId, 888L);
        verify(eventPublisher).publish(any(), any());
    }

    @Test
    void updateProduct_whenMediaReferenceInvalid_throwsAndSkipsSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "thumbnailMediaId", 777L);
        ReflectionTestUtils.setField(request, "categoryId", 10L);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsById(10L)).thenReturn(true);
        when(mediaReferenceService.resolveMediaUrl(777L))
                .thenThrow(new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE));

        assertThatThrownBy(() -> productCommandService.updateProduct(itemId, request, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_MEDIA_REFERENCE);

        verifyNoInteractions(itemThumbnailSyncService);
    }

    @Test
    void updateProduct_withClearThumbnail_clearsAndSchedulesSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "clearThumbnail", true);
        ReflectionTestUtils.setField(request, "categoryId", 10L);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsById(10L)).thenReturn(true);
        when(itemOptionRepository.findByItemId(itemId)).thenReturn(List.of());
        when(shippingInfoRepository.findByItemId(itemId)).thenReturn(Optional.empty());
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(java.util.List.of());

        productCommandService.updateProduct(itemId, request, sellerId);

        assertThat(item.getThumbnailMediaId()).isNull();
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, null, true);
        verify(mediaReferenceService, org.mockito.Mockito.never()).resolveMediaUrl(any());
    }

    @Test
    void updateProduct_whenThumbnailAndClearBothProvided_throwsValidationError() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "thumbnailMediaId", 700L);
        ReflectionTestUtils.setField(request, "clearThumbnail", true);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> productCommandService.updateProduct(itemId, request, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_THUMBNAIL_UPDATE_REQUEST);

        verifyNoInteractions(itemThumbnailSyncService);
    }

    @Test
    void updateProduct_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item goodsItem = createItem(itemId, sellerId, ItemType.GOODS);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "updated");

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(goodsItem));

        assertThatThrownBy(() -> productCommandService.updateProduct(itemId, request, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(itemThumbnailSyncService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createProduct_whenCategoryNotFound_throwsBusinessError() {
        ProductCreateRequest request = new ProductCreateRequest();
        ReflectionTestUtils.setField(request, "title", "new item");
        ReflectionTestUtils.setField(request, "description", "desc");
        ReflectionTestUtils.setField(request, "price", 1000L);
        ReflectionTestUtils.setField(request, "storeId", 10L);
        ReflectionTestUtils.setField(request, "categoryId", 999L);

        when(categoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> productCommandService.createProduct(request, 77L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);

        verifyNoInteractions(itemRepository, itemThumbnailSyncService, eventPublisher);
    }

    @Test
    void updateProduct_whenCategoryNotFound_throwsBusinessError() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "categoryId", 999L);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(categoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> productCommandService.updateProduct(itemId, request, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);

        verifyNoInteractions(itemThumbnailSyncService, eventPublisher);
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
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, null, true);
        assertThat(item.getThumbnailMediaId()).isNull();
    }

    @Test
    void delete_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item goodsItem = createItem(itemId, sellerId, ItemType.GOODS);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(goodsItem));

        assertThatThrownBy(() -> productCommandService.delete(itemId, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);
    }

    @Test
    void updateProduct_whenPartialRequest_returnsCurrentSnapshot() {
        Long itemId = 3L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ProductUpdateRequest request = new ProductUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "title-only-update");

        ItemOption option = ItemOption.create(itemId, "옵션A", 100L, 3);
        ShippingInfo shippingInfo = ShippingInfo.create(itemId, 3000L, 50000L, 2, "교환/반품 정책");

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(itemOptionRepository.findByItemId(itemId)).thenReturn(List.of(option));
        when(shippingInfoRepository.findByItemId(itemId)).thenReturn(Optional.of(shippingInfo));
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of());

        GoodsDetailResponse response = productCommandService.updateProduct(itemId, request, sellerId);

        assertThat(response.getOptions()).hasSize(1);
        assertThat(response.getOptions().get(0).getOptionName()).isEqualTo("옵션A");
        assertThat(response.getShippingInfo()).isNotNull();
        assertThat(response.getShippingInfo().getShippingFee()).isEqualTo(3000L);
    }

    private Item createItem(Long id, Long sellerId) {
        return createItem(id, sellerId, ItemType.PRODUCT);
    }

    private Item createItem(Long id, Long sellerId, ItemType itemType) {
        Item item = Item.create(
                "item",
                "desc",
                1000L,
                itemType,
                null,
                sellerId,
                100L,
                999L
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
