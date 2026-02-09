package com.example.product.service;

import com.example.core.exception.BusinessException;
import com.example.product.domain.item.Item;
import com.example.product.domain.item.ItemRepository;
import com.example.product.domain.item.ItemType;
import com.example.product.domain.performance.*;
import com.example.product.dto.performance.*;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceService {

    private final ItemRepository itemRepository;
    private final PerformanceRepository performanceRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final CastMemberRepository castMemberRepository;

    @Transactional
    public PerformanceDetailResponse create(PerformanceCreateRequest request, Long sellerId) {
        Item item = Item.create(
                request.getTitle(), request.getDescription(), request.getPrice(),
                ItemType.PERFORMANCE, request.getCategoryId(), sellerId, request.getThumbnailUrl());
        itemRepository.save(item);

        Performance performance = Performance.create(
                item.getId(), request.getVenue(), request.getPerformanceDate(),
                request.getPerformanceTime(), request.getTotalSeats());
        performanceRepository.save(performance);

        List<SeatGrade> seatGrades = request.getSeatGrades().stream()
                .map(sg -> SeatGrade.create(performance.getId(), sg.getGradeName(),
                        sg.getPrice(), sg.getTotalQuantity(), sg.getFundingQuantity()))
                .toList();
        seatGradeRepository.saveAll(seatGrades);

        List<CastMember> castMembers = List.of();
        if (request.getCastMembers() != null && !request.getCastMembers().isEmpty()) {
            castMembers = request.getCastMembers().stream()
                    .map(cm -> CastMember.create(performance.getId(), cm.getName(),
                            cm.getRole(), cm.getProfileImageUrl()))
                    .toList();
            castMemberRepository.saveAll(castMembers);
        }

        return PerformanceDetailResponse.of(item, performance, seatGrades, castMembers);
    }

    public PerformanceDetailResponse findById(Long itemId) {
        Item item = getItem(itemId);
        Performance performance = getPerformance(itemId);
        List<SeatGrade> seatGrades = seatGradeRepository.findByPerformanceIdOrderByPriceDesc(performance.getId());
        List<CastMember> castMembers = castMemberRepository.findByPerformanceId(performance.getId());
        return PerformanceDetailResponse.of(item, performance, seatGrades, castMembers);
    }

    public List<PerformanceListResponse> findList(Long cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        List<Item> items;

        if (cursor == null) {
            items = itemRepository.findByItemTypeOrderByIdDesc(ItemType.PERFORMANCE, pageable);
        } else {
            items = itemRepository.findByItemTypeAndIdLessThan(ItemType.PERFORMANCE, cursor, pageable);
        }

        return items.stream().map(item -> {
            Performance perf = performanceRepository.findByItemId(item.getId())
                    .orElse(null);
            if (perf == null) return null;
            return PerformanceListResponse.of(item, perf);
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public PerformanceDetailResponse update(Long itemId, PerformanceUpdateRequest request, Long sellerId) {
        Item item = getItem(itemId);
        validateOwnership(item, sellerId);

        item.update(request.getTitle(), request.getDescription(), request.getPrice(),
                request.getCategoryId(), request.getThumbnailUrl());

        Performance performance = getPerformance(itemId);
        performance.update(request.getVenue(), request.getPerformanceDate(),
                request.getPerformanceTime(), request.getTotalSeats());

        // 좌석 등급 교체
        if (request.getSeatGrades() != null) {
            seatGradeRepository.deleteAllByPerformanceId(performance.getId());
            List<SeatGrade> seatGrades = request.getSeatGrades().stream()
                    .map(sg -> SeatGrade.create(performance.getId(), sg.getGradeName(),
                            sg.getPrice(), sg.getTotalQuantity(), sg.getFundingQuantity()))
                    .toList();
            seatGradeRepository.saveAll(seatGrades);
        }

        // 출연진 교체
        if (request.getCastMembers() != null) {
            castMemberRepository.deleteAllByPerformanceId(performance.getId());
            List<CastMember> castMembers = request.getCastMembers().stream()
                    .map(cm -> CastMember.create(performance.getId(), cm.getName(),
                            cm.getRole(), cm.getProfileImageUrl()))
                    .toList();
            castMemberRepository.saveAll(castMembers);
        }

        List<SeatGrade> seatGrades = seatGradeRepository.findByPerformanceIdOrderByPriceDesc(performance.getId());
        List<CastMember> castMembers = castMemberRepository.findByPerformanceId(performance.getId());
        return PerformanceDetailResponse.of(item, performance, seatGrades, castMembers);
    }

    @Transactional
    public void delete(Long itemId, Long sellerId) {
        Item item = getItem(itemId);
        validateOwnership(item, sellerId);
        item.softDelete();
    }

    private Item getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
    }

    private Performance getPerformance(Long itemId) {
        return performanceRepository.findByItemId(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PERFORMANCE_NOT_FOUND));
    }

    private void validateOwnership(Item item, Long sellerId) {
        if (!item.isOwnedBy(sellerId)) {
            throw new BusinessException(ProductErrorCode.UNAUTHORIZED_ACCESS);
        }
    }
}
