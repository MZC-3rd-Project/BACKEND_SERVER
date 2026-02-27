package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.content.ItemContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemContentService itemContentService;

    public GoodsDetailResponse findProductById(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        validateItemType(item, ItemType.PRODUCT);
        validateVisibleStatus(item);
        List<ItemOption> options = itemOptionRepository.findByItemId(itemId);
        ShippingInfo shippingInfo = shippingInfoRepository.findByItemId(itemId).orElse(null);
        ItemContentSnapshot contentSnapshot = itemContentService.findByItemId(itemId);
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        return GoodsDetailResponse.of(item, options, shippingInfo, List.of(), contentSnapshot, images);
    }

    public GoodsDetailResponse findSellerProductById(Long itemId, Long sellerId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        validateItemType(item, ItemType.PRODUCT);
        item.validateOwnership(sellerId);
        List<ItemOption> options = itemOptionRepository.findByItemId(itemId);
        ShippingInfo shippingInfo = shippingInfoRepository.findByItemId(itemId).orElse(null);
        ItemContentSnapshot contentSnapshot = itemContentService.findByItemId(itemId);
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        return GoodsDetailResponse.of(item, options, shippingInfo, List.of(), contentSnapshot, images);
    }

    private static final List<ItemStatus> VISIBLE_STATUSES = List.of(
            ItemStatus.FUNDING, ItemStatus.FUNDED, ItemStatus.ON_SALE, ItemStatus.HOT_DEAL);

    public CursorResponse<GoodsDetailResponse> findProductList(String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<Item> items = cursorId == null
                ? itemRepository.findByItemTypeAndStatusIn(ItemType.PRODUCT, VISIBLE_STATUSES, pageable)
                : itemRepository.findByItemTypeAndStatusInAndIdLessThan(ItemType.PRODUCT, VISIBLE_STATUSES, cursorId, pageable);

        boolean hasNext = items.size() > size;
        List<Item> pageItems = hasNext ? items.subList(0, size) : items;
        List<Long> itemIds = pageItems.stream().map(Item::getId).toList();

        Map<Long, List<ItemOption>> optionsMap = itemOptionRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(ItemOption::getItemId));

        Map<Long, ShippingInfo> shippingMap = shippingInfoRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(ShippingInfo::getItemId, s -> s));

        Map<Long, List<ItemImage>> imageMap = itemImageRepository
                .findByItemIdInOrderByItemIdAscSortOrderAsc(itemIds).stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));
        Map<Long, ItemContentSnapshot> contentMap = itemContentService.findByItemIds(itemIds);

        List<GoodsDetailResponse> content = pageItems.stream().map(item -> {
            List<ItemOption> options = optionsMap.getOrDefault(item.getId(), List.of());
            ShippingInfo shippingInfo = shippingMap.get(item.getId());
            List<ItemImage> images = imageMap.getOrDefault(item.getId(), List.of());
            ItemContentSnapshot contentSnapshot = contentMap.getOrDefault(item.getId(), ItemContentSnapshot.empty());
            return GoodsDetailResponse.of(item, options, shippingInfo, List.of(), contentSnapshot, images);
        }).toList();

        String nextCursor = hasNext ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId()) : null;
        return CursorResponse.of(content, nextCursor);
    }

    private void validateItemType(Item item, ItemType expectedType) {
        if (item.getItemType() != expectedType) {
            throw new BusinessException(ProductErrorCode.ITEM_TYPE_MISMATCH);
        }
    }

    private void validateVisibleStatus(Item item) {
        if (!VISIBLE_STATUSES.contains(item.getStatus())) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_FOUND);
        }
    }
}
