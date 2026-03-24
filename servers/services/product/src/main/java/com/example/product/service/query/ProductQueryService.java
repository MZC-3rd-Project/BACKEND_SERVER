package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemRepository;
import com.example.product.service.query.assembler.ProductDetailAssembler;
import com.example.product.service.query.detail.GoodsItemDetailView;
import com.example.product.service.query.detail.ProductItemDetailReader;
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
    private final ItemAccessPolicy itemAccessPolicy;
    private final ProductItemDetailReader productItemDetailReader;
    private final ProductDetailAssembler productDetailAssembler;

    public GoodsDetailResponse findProductById(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        itemAccessPolicy.validatePublicAccess(item, ItemType.PRODUCT);
        GoodsItemDetailView detailView = productItemDetailReader.read(item);
        return productDetailAssembler.toResponse(detailView);
    }

    @UseWriteDataSource
    public GoodsDetailResponse findSellerProductById(Long itemId, Long sellerId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        itemAccessPolicy.validateSellerAccess(item, ItemType.PRODUCT, sellerId);
        GoodsItemDetailView detailView = productItemDetailReader.read(item);
        return productDetailAssembler.toResponse(detailView);
    }

    @UseWriteDataSource
    public CursorResponse<GoodsDetailResponse> findSellerProductList(String cursor, int size, Long sellerId) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<Item> items = cursorId == null
                ? itemRepository.findBySellerIdOrderByIdDesc(sellerId, pageable)
                : itemRepository.findBySellerIdAndIdLessThanOrderByIdDesc(sellerId, cursorId, pageable);

        boolean hasNext = items.size() > size;
        List<Item> pageItems = hasNext ? items.subList(0, size) : items;
        var detailViews = productItemDetailReader.readAll(pageItems);
        List<GoodsDetailResponse> content = pageItems.stream()
                .map(item -> productDetailAssembler.toResponse(detailViews.get(item.getId())))
                .toList();

        String nextCursor = hasNext ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId()) : null;
        return CursorResponse.of(content, nextCursor);
    }

    public CursorResponse<GoodsDetailResponse> findProductList(String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<ItemStatus> visibleStatuses = itemAccessPolicy.visibleStatuses();

        List<Item> items = cursorId == null
                ? itemRepository.findByItemTypeAndStatusIn(ItemType.PRODUCT, visibleStatuses, pageable)
                : itemRepository.findByItemTypeAndStatusInAndIdLessThan(ItemType.PRODUCT, visibleStatuses, cursorId, pageable);

        boolean hasNext = items.size() > size;
        List<Item> pageItems = hasNext ? items.subList(0, size) : items;
        var detailViews = productItemDetailReader.readAll(pageItems);
        List<GoodsDetailResponse> content = pageItems.stream()
                .map(item -> productDetailAssembler.toResponse(detailViews.get(item.getId())))
                .toList();

        String nextCursor = hasNext ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId()) : null;
        return CursorResponse.of(content, nextCursor);
    }
}
