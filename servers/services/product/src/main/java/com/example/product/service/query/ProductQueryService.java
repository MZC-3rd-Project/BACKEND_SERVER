package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final ShippingInfoRepository shippingInfoRepository;

    public GoodsDetailResponse findProductById(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        List<ItemOption> options = itemOptionRepository.findByItemId(itemId);
        ShippingInfo shippingInfo = shippingInfoRepository.findByItemId(itemId).orElse(null);
        return GoodsDetailResponse.of(item, options, shippingInfo, List.of());
    }

    public CursorResponse<GoodsDetailResponse> findProductList(String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<Item> items = cursorId == null
                ? itemRepository.findByItemTypeOrderByIdDesc(ItemType.PRODUCT, pageable)
                : itemRepository.findByItemTypeAndIdLessThan(ItemType.PRODUCT, cursorId, pageable);

        boolean hasNext = items.size() > size;
        List<Item> pageItems = hasNext ? items.subList(0, size) : items;

        List<GoodsDetailResponse> content = pageItems.stream().map(item -> {
            List<ItemOption> options = itemOptionRepository.findByItemId(item.getId());
            ShippingInfo shippingInfo = shippingInfoRepository.findByItemId(item.getId()).orElse(null);
            return GoodsDetailResponse.of(item, options, shippingInfo, List.of());
        }).toList();

        String nextCursor = hasNext ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId()) : null;
        return CursorResponse.of(content, nextCursor);
    }
}
