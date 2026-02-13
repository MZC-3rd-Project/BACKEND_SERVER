package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.dto.performance.response.PerformanceListResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.CastMember;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.*;
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
    private final SeatGradeRepository seatGradeRepository;
    private final CastMemberRepository castMemberRepository;

    private static final List<ItemStatus> VISIBLE_STATUSES = List.of(
            ItemStatus.FUNDING, ItemStatus.FUNDED, ItemStatus.ON_SALE, ItemStatus.HOT_DEAL);

    public PerformanceDetailResponse findById(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
        Performance performance = performanceRepository.findByItemId(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PERFORMANCE_NOT_FOUND));
        List<SeatGrade> seatGrades = seatGradeRepository.findByPerformanceIdOrderByPriceDesc(performance.getId());
        List<CastMember> castMembers = castMemberRepository.findByPerformanceId(performance.getId());
        return PerformanceDetailResponse.of(item, performance, seatGrades, castMembers);
    }

    public CursorResponse<PerformanceListResponse> findList(String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<Item> items = cursorId == null
                ? itemRepository.findByItemTypeAndStatusIn(ItemType.PERFORMANCE, VISIBLE_STATUSES, pageable)
                : itemRepository.findByItemTypeAndStatusInAndIdLessThan(ItemType.PERFORMANCE, VISIBLE_STATUSES, cursorId, pageable);

        boolean hasNext = items.size() > size;
        List<Item> pageItems = hasNext ? items.subList(0, size) : items;

        List<Long> itemIds = pageItems.stream().map(Item::getId).toList();
        Map<Long, Performance> perfMap = performanceRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(Performance::getItemId, p -> p));

        List<PerformanceListResponse> content = pageItems.stream()
                .filter(item -> perfMap.containsKey(item.getId()))
                .map(item -> PerformanceListResponse.of(item, perfMap.get(item.getId())))
                .toList();

        String nextCursor = hasNext ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId()) : null;
        return CursorResponse.of(content, nextCursor);
    }
}
