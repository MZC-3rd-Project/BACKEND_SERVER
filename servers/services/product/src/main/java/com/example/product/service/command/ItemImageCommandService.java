package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.product.dto.image.request.ItemImageRequest;
import com.example.product.dto.image.response.ItemImageResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemImageCommandService {

    private final ItemImageRepository itemImageRepository;
    private final ItemRepository itemRepository;

    public List<ItemImageResponse> addImages(Long itemId, List<ItemImageRequest> requests, Long userId) {
        validateOwnership(itemId, userId);
        List<ItemImage> images = requests.stream()
                .map(req -> ItemImage.create(itemId, req.getImageUrl(), req.getSortOrder(), req.isThumbnail()))
                .toList();
        return itemImageRepository.saveAll(images).stream()
                .map(ItemImageResponse::from).toList();
    }

    public void deleteImage(Long imageId, Long userId) {
        ItemImage image = itemImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.IMAGE_NOT_FOUND));
        validateOwnership(image.getItemId(), userId);
        image.softDelete();
    }

    public List<ItemImageResponse> reorder(Long itemId, List<Long> imageIds, Long userId) {
        validateOwnership(itemId, userId);
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        for (int i = 0; i < imageIds.size(); i++) {
            Long targetId = imageIds.get(i);
            for (ItemImage img : images) {
                if (img.getId().equals(targetId)) {
                    img.updateSortOrder(i);
                    break;
                }
            }
        }
        return images.stream().sorted((a, b) -> a.getSortOrder() - b.getSortOrder())
                .map(ItemImageResponse::from).toList();
    }

    private void validateOwnership(Long itemId, Long userId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        item.validateOwnership(userId);
    }
}
