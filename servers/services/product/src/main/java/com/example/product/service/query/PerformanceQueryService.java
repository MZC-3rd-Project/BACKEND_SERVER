package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.dto.performance.response.PerformanceListResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.service.query.assembler.PerformanceDetailAssembler;
import com.example.product.service.query.assembler.PerformanceListAssembler;
import com.example.product.service.query.detail.PerformanceItemDetailReader;
import com.example.product.service.query.detail.PerformanceItemDetailView;
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
public class PerformanceQueryService {

    private final ItemRepository itemRepository;
    private final PerformanceRepository performanceRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemAccessPolicy itemAccessPolicy;
    private final PerformanceItemDetailReader performanceItemDetailReader;
    private final PerformanceDetailAssembler performanceDetailAssembler;
    private final PerformanceListAssembler performanceListAssembler;

    public PerformanceDetailResponse findById(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        itemAccessPolicy.validatePublicAccess(item, ItemType.PERFORMANCE);
        PerformanceItemDetailView detailView = performanceItemDetailReader.read(item);
        return performanceDetailAssembler.toResponse(detailView);
    }

    @UseWriteDataSource
    public PerformanceDetailResponse findSellerById(Long itemId, Long sellerId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        itemAccessPolicy.validateSellerAccess(item, ItemType.PERFORMANCE, sellerId);
        PerformanceItemDetailView detailView = performanceItemDetailReader.read(item);
        return performanceDetailAssembler.toResponse(detailView);
    }

    public CursorResponse<PerformanceListResponse> findList(String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<ItemStatus> visibleStatuses = itemAccessPolicy.visibleStatuses();

        List<Item> items = cursorId == null
                ? itemRepository.findByItemTypeAndStatusIn(ItemType.PERFORMANCE, visibleStatuses, pageable)
                : itemRepository.findByItemTypeAndStatusInAndIdLessThan(ItemType.PERFORMANCE, visibleStatuses, cursorId, pageable);

        boolean hasNext = items.size() > size;
        List<Item> pageItems = hasNext ? items.subList(0, size) : items;

        List<Long> itemIds = pageItems.stream().map(Item::getId).toList();
        Map<Long, Performance> perfMap = performanceRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(Performance::getItemId, p -> p));
        Map<Long, List<ItemImage>> imageMap = itemImageRepository
                .findByItemIdInOrderByItemIdAscSortOrderAsc(itemIds).stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));

        List<PerformanceListResponse> content = pageItems.stream()
                .filter(item -> perfMap.containsKey(item.getId()))
                .map(item -> performanceListAssembler.toResponse(
                        item,
                        perfMap.get(item.getId()),
                        imageMap.getOrDefault(item.getId(), List.of())
                ))
                .toList();

        String nextCursor = hasNext ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId()) : null;
        return CursorResponse.of(content, nextCursor);
    }
}
