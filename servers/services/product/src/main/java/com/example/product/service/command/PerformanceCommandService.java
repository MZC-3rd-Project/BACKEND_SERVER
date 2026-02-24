package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.dto.performance.request.PerformanceCreateRequest;
import com.example.product.dto.performance.request.PerformanceUpdateRequest;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.CastMember;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.event.ItemCreatedEvent;
import com.example.product.event.ItemUpdatedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.*;
import com.example.product.service.command.image.ItemThumbnailSyncService;
import com.example.product.service.command.image.MediaReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PerformanceCommandService {

    private final ItemRepository itemRepository;
    private final PerformanceRepository performanceRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final CastMemberRepository castMemberRepository;
    private final ItemImageRepository itemImageRepository;
    private final MediaReferenceService mediaReferenceService;
    private final ItemThumbnailSyncService itemThumbnailSyncService;
    private final EventPublisher eventPublisher;

    public PerformanceDetailResponse create(PerformanceCreateRequest request, Long sellerId) {
        // TODO: store-service 연동 후 sellerId-storeId 소유권 검증을 추가한다.
        mediaReferenceService.resolveMediaUrl(request.getThumbnailMediaId());
        Item item = Item.create(
                request.getTitle(), request.getDescription(), request.getPrice(),
                ItemType.PERFORMANCE, request.getCategoryId(), sellerId, request.getStoreId(),
                request.getThumbnailMediaId());
        itemRepository.save(item);
        if (request.getThumbnailMediaId() != null) {
            itemThumbnailSyncService.syncAfterCommit(item.getId(), request.getThumbnailMediaId());
        }

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

        List<ItemCreatedEvent.StockItemInfo> stockItems = seatGrades.stream()
                .map(sg -> new ItemCreatedEvent.StockItemInfo(
                        "SEAT_GRADE", sg.getId(), sg.getTotalQuantity()))
                .toList();

        eventPublisher.publish(
                new ItemCreatedEvent(
                        item.getId(),
                        item.getTitle(),
                        item.getItemType().name(),
                        item.getPrice(),
                        item.getThumbnailMediaId(),
                        System.currentTimeMillis(),
                        item.getStatus().name(),
                        sellerId,
                        request.getStoreId(),
                        stockItems
                ),
                EventMetadata.of("Item", String.valueOf(item.getId())));

        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(item.getId());
        return PerformanceDetailResponse.of(item, performance, seatGrades, castMembers, images);
    }

    public PerformanceDetailResponse update(Long itemId, PerformanceUpdateRequest request, Long sellerId) {
        Item item = getItem(itemId);
        item.validateOwnership(sellerId);
        if (!item.isEditable()) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_EDITABLE);
        }

        boolean clearThumbnail = Boolean.TRUE.equals(request.getClearThumbnail());
        Long thumbnailMediaId = request.getThumbnailMediaId();
        if (clearThumbnail && thumbnailMediaId != null) {
            throw new BusinessException(ProductErrorCode.INVALID_THUMBNAIL_UPDATE_REQUEST);
        }

        if (clearThumbnail) {
            item.update(request.getTitle(), request.getDescription(), request.getPrice(),
                    request.getCategoryId(), null);
            item.clearThumbnail();
            itemThumbnailSyncService.syncAfterCommit(item.getId(), null, true);
        } else {
            mediaReferenceService.resolveMediaUrl(thumbnailMediaId);
            item.update(request.getTitle(), request.getDescription(), request.getPrice(),
                    request.getCategoryId(), thumbnailMediaId);
            if (thumbnailMediaId != null) {
                itemThumbnailSyncService.syncAfterCommit(item.getId(), thumbnailMediaId);
            }
        }

        Performance performance = getPerformance(itemId);
        performance.update(request.getVenue(), request.getPerformanceDate(),
                request.getPerformanceTime(), request.getTotalSeats());

        if (request.getSeatGrades() != null) {
            seatGradeRepository.softDeleteAllByPerformanceId(performance.getId());
            List<SeatGrade> seatGrades = request.getSeatGrades().stream()
                    .map(sg -> SeatGrade.create(performance.getId(), sg.getGradeName(),
                            sg.getPrice(), sg.getTotalQuantity(), sg.getFundingQuantity()))
                    .toList();
            seatGradeRepository.saveAll(seatGrades);
        }

        if (request.getCastMembers() != null) {
            castMemberRepository.softDeleteAllByPerformanceId(performance.getId());
            List<CastMember> castMembers = request.getCastMembers().stream()
                    .map(cm -> CastMember.create(performance.getId(), cm.getName(),
                            cm.getRole(), cm.getProfileImageUrl()))
                    .toList();
            castMemberRepository.saveAll(castMembers);
        }

        eventPublisher.publish(
                new ItemUpdatedEvent(
                        item.getId(),
                        item.getTitle(),
                        item.getPrice(),
                        item.getThumbnailMediaId(),
                        System.currentTimeMillis()
                ),
                EventMetadata.of("Item", String.valueOf(item.getId())));

        List<SeatGrade> seatGrades = seatGradeRepository.findByPerformanceIdOrderByPriceDesc(performance.getId());
        List<CastMember> castMembers = castMemberRepository.findByPerformanceId(performance.getId());
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrder(itemId);
        return PerformanceDetailResponse.of(item, performance, seatGrades, castMembers, images);
    }

    public void delete(Long itemId, Long sellerId) {
        Item item = getItem(itemId);
        item.validateOwnership(sellerId);
        if (!item.isDeletable()) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_DELETABLE);
        }
        item.softDelete();

        Performance performance = performanceRepository.findByItemId(itemId).orElse(null);
        if (performance != null) {
            seatGradeRepository.softDeleteAllByPerformanceId(performance.getId());
            castMemberRepository.softDeleteAllByPerformanceId(performance.getId());
            performanceRepository.softDeleteByItemId(itemId);
        }
        itemImageRepository.softDeleteAllByItemId(itemId);
        item.clearThumbnail();
        itemThumbnailSyncService.syncAfterCommit(itemId, null, true);
    }

    private Item getItem(Long itemId) {
        return itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));
    }

    private Performance getPerformance(Long itemId) {
        return performanceRepository.findByItemId(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PERFORMANCE_NOT_FOUND));
    }

}
