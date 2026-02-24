package com.example.product.service.command.image;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.product.dto.image.request.ItemImageRequest;
import com.example.product.dto.image.response.ItemImageResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemImageCommandServiceTest {

    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private MediaReferenceService mediaReferenceService;
    @Mock
    private ItemThumbnailSyncService itemThumbnailSyncService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ItemImageCommandService itemImageCommandService;

    @Test
    void addImages_normalizesAndSchedulesSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);

        ItemImageRequest first = new ItemImageRequest();
        ReflectionTestUtils.setField(first, "mediaId", 101L);
        ReflectionTestUtils.setField(first, "sortOrder", 5);
        ReflectionTestUtils.setField(first, "isThumbnail", false);

        ItemImageRequest second = new ItemImageRequest();
        ReflectionTestUtils.setField(second, "mediaId", 102L);
        ReflectionTestUtils.setField(second, "sortOrder", 0);
        ReflectionTestUtils.setField(second, "isThumbnail", true);

        ItemImage image1 = createImage(11L, itemId, 101L, 5, false);
        ItemImage image2 = createImage(12L, itemId, 102L, 0, true);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of(image2, image1));
        when(itemImageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItemImageResponse> responses = itemImageCommandService.addImages(itemId, List.of(first, second), sellerId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getMediaId()).isEqualTo(102L);
        assertThat(responses.get(0).getSortOrder()).isEqualTo(0);
        assertThat(responses.get(0).getIsThumbnail()).isTrue();
        assertThat(responses.get(1).getMediaId()).isEqualTo(101L);
        assertThat(responses.get(1).getSortOrder()).isEqualTo(1);
        assertThat(responses.get(1).getIsThumbnail()).isFalse();
        assertThat(item.getThumbnailMediaId()).isEqualTo(102L);

        verify(mediaReferenceService).validateMediaReferences(List.of(101L, 102L));
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, 102L, List.of(101L));
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addImages_whenMediaResolutionFails_throwsAndSkipsSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);

        ItemImageRequest request = new ItemImageRequest();
        ReflectionTestUtils.setField(request, "mediaId", 101L);
        ReflectionTestUtils.setField(request, "sortOrder", 0);
        ReflectionTestUtils.setField(request, "isThumbnail", true);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        org.mockito.Mockito.doThrow(new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE))
                .when(mediaReferenceService).validateMediaReferences(List.of(101L));

        assertThatThrownBy(() -> itemImageCommandService.addImages(itemId, List.of(request), sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_MEDIA_REFERENCE);

        verify(itemThumbnailSyncService, org.mockito.Mockito.never())
                .syncAfterCommit(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void reorder_rejectsInvalidRequest() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ItemImage image1 = createImage(11L, itemId, 101L, 0, true);
        ItemImage image2 = createImage(12L, itemId, 102L, 1, false);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of(image1, image2));

        assertThatThrownBy(() -> itemImageCommandService.reorder(itemId, List.of(image1.getId()), sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_IMAGE_REORDER_REQUEST);
    }

    @Test
    void reorder_validRequest_updatesOrderAndSchedulesSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ItemImage thumbnail = createImage(11L, itemId, 101L, 0, true);
        ItemImage gallery = createImage(12L, itemId, 102L, 1, false);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId))
                .thenReturn(List.of(thumbnail, gallery))
                .thenReturn(List.of(gallery, thumbnail));

        List<ItemImageResponse> responses = itemImageCommandService.reorder(itemId, List.of(gallery.getId(), thumbnail.getId()), sellerId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getMediaId()).isEqualTo(102L);
        assertThat(responses.get(0).getSortOrder()).isEqualTo(0);
        assertThat(responses.get(1).getMediaId()).isEqualTo(101L);
        assertThat(responses.get(1).getSortOrder()).isEqualTo(1);
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, 101L, List.of(102L));
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteImage_whenLastImage_thenClearsThumbnailAndSync() {
        Long itemId = 1L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ItemImage image = createImage(11L, itemId, 101L, 0, true);

        when(itemImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of());

        itemImageCommandService.deleteImage(image.getId(), sellerId);

        assertThat(item.getThumbnailMediaId()).isNull();
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, null, List.of());
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteImage_whenThumbnailRemoved_promotesRemainingImage() {
        Long itemId = 2L;
        Long sellerId = 10L;
        Item item = createItem(itemId, sellerId);
        ItemImage deletedThumbnail = createImage(21L, itemId, 201L, 0, true);
        ItemImage remaining = createImage(22L, itemId, 202L, 1, false);

        when(itemImageRepository.findById(deletedThumbnail.getId())).thenReturn(Optional.of(deletedThumbnail));
        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of(remaining));

        itemImageCommandService.deleteImage(deletedThumbnail.getId(), sellerId);

        assertThat(item.getThumbnailMediaId()).isEqualTo(202L);
        verify(itemThumbnailSyncService).syncAfterCommit(itemId, 202L, List.of());
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
                999L
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private ItemImage createImage(Long id, Long itemId, Long mediaId, int sortOrder, boolean thumbnail) {
        ItemImage image = ItemImage.create(itemId, mediaId, sortOrder, thumbnail);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }
}
