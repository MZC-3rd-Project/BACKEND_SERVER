package com.example.product.service;

import com.example.core.exception.BusinessException;
import com.example.product.domain.image.ItemImage;
import com.example.product.domain.image.ItemImageRepository;
import com.example.product.dto.image.ItemImageRequest;
import com.example.product.dto.image.ItemImageResponse;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemImageService {

    private final ItemImageRepository itemImageRepository;

    @Transactional
    public List<ItemImageResponse> addImages(Long itemId, List<ItemImageRequest> requests) {
        List<ItemImage> images = requests.stream()
                .map(req -> ItemImage.create(itemId, req.getImageUrl(), req.getSortOrder(), req.isThumbnail()))
                .toList();
        return itemImageRepository.saveAll(images).stream()
                .map(ItemImageResponse::from).toList();
    }

    public List<ItemImageResponse> findByItemId(Long itemId) {
        return itemImageRepository.findByItemIdOrderBySortOrder(itemId).stream()
                .map(ItemImageResponse::from).toList();
    }

    @Transactional
    public void deleteImage(Long imageId) {
        ItemImage image = itemImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        image.softDelete();
    }

    @Transactional
    public List<ItemImageResponse> reorder(Long itemId, List<Long> imageIds) {
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
}
