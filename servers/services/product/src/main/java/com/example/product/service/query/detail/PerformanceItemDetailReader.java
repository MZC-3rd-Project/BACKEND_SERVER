package com.example.product.service.query.detail;

import com.example.core.exception.BusinessException;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.performance.CastMember;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CastMemberRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import com.example.product.service.content.ItemContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceItemDetailReader implements ItemDetailReader<PerformanceItemDetailView> {

    private final PerformanceRepository performanceRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final CastMemberRepository castMemberRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemContentService itemContentService;
    private final ItemCategoryDetailResolver itemCategoryDetailResolver;

    @Override
    public PerformanceItemDetailView read(Item item) {
        PerformanceItemDetailView detailView = readAll(List.of(item)).get(item.getId());
        if (detailView == null) {
            throw new BusinessException(ProductErrorCode.PERFORMANCE_NOT_FOUND);
        }
        return detailView;
    }

    @Override
    public Map<Long, PerformanceItemDetailView> readAll(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, Performance> performanceMap = performanceRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(Performance::getItemId, Function.identity()));
        List<Long> performanceIds = performanceMap.values().stream().map(Performance::getId).toList();
        Map<Long, List<SeatGrade>> seatGradeMap = readSeatGrades(performanceIds);
        Map<Long, List<CastMember>> castMemberMap = readCastMembers(performanceIds);
        Map<Long, ItemContentSnapshot> contentMap = itemContentService.findByItemIds(itemIds);
        Map<Long, ItemCategoryDetailView> categoryDetailMap = itemCategoryDetailResolver.resolve(items);
        Map<Long, List<ItemImage>> imageMap = itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(itemIds).stream()
                .collect(Collectors.groupingBy(ItemImage::getItemId));

        Map<Long, PerformanceItemDetailView> detailViews = new LinkedHashMap<>();
        for (Item item : items) {
            Performance performance = performanceMap.get(item.getId());
            if (performance == null) {
                continue;
            }
            detailViews.put(item.getId(), new PerformanceItemDetailView(
                    item,
                    categoryDetailMap.get(item.getId()),
                    performance,
                    seatGradeMap.getOrDefault(performance.getId(), List.of()),
                    castMemberMap.getOrDefault(performance.getId(), List.of()),
                    contentMap.getOrDefault(item.getId(), ItemContentSnapshot.empty()),
                    imageMap.getOrDefault(item.getId(), List.of())
            ));
        }
        return detailViews;
    }

    private Map<Long, List<SeatGrade>> readSeatGrades(List<Long> performanceIds) {
        if (performanceIds.isEmpty()) {
            return Map.of();
        }

        return seatGradeRepository.findByPerformanceIdIn(performanceIds).stream()
                .collect(Collectors.groupingBy(
                        SeatGrade::getPerformanceId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                seatGrades -> seatGrades.stream()
                                        .sorted(Comparator.comparing(SeatGrade::getPrice, Comparator.reverseOrder()))
                                        .toList()
                        )
                ));
    }

    private Map<Long, List<CastMember>> readCastMembers(List<Long> performanceIds) {
        if (performanceIds.isEmpty()) {
            return Map.of();
        }

        return castMemberRepository.findByPerformanceIdIn(performanceIds).stream()
                .collect(Collectors.groupingBy(CastMember::getPerformanceId));
    }
}
