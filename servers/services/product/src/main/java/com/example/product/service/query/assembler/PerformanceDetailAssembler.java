package com.example.product.service.query.assembler;

import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.dto.item.response.ItemPriceMetaResponse;
import com.example.product.dto.performance.response.CastMemberResponse;
import com.example.product.dto.performance.response.PerformanceDetailResponse;
import com.example.product.dto.performance.response.SeatGradeResponse;
import com.example.product.service.query.detail.PerformanceItemDetailView;
import org.springframework.stereotype.Component;

@Component
public class PerformanceDetailAssembler {

    public PerformanceDetailResponse toResponse(PerformanceItemDetailView detailView) {
        return PerformanceDetailResponse.builder()
                .id(detailView.item().getId())
                .title(detailView.item().getTitle())
                .description(detailView.item().getDescription())
                .price(detailView.item().getPrice())
                .status(detailView.item().getStatus().name())
                .itemType(detailView.item().getItemType().name())
                .images(ItemImagesResponse.from(detailView.images(), detailView.item().getThumbnailMediaId()))
                .averageRating(detailView.item().getAverageRating())
                .reviewCount(detailView.item().getReviewCount())
                .categoryId(detailView.item().getCategoryId())
                .categoryName(detailView.categoryDetail() != null ? detailView.categoryDetail().categoryName() : null)
                .categoryPath(detailView.categoryDetail() != null ? detailView.categoryDetail().categoryPath() : java.util.List.of())
                .sellerId(detailView.item().getSellerId())
                .storeId(detailView.item().getStoreId())
                .tags(detailView.contentSnapshot().tags())
                .features(detailView.contentSnapshot().features())
                .detailSections(detailView.contentSnapshot().detailSections())
                .priceMeta(ItemPriceMetaResponse.fromSeatGrades(detailView.item().getPrice(), detailView.seatGrades()))
                .venue(detailView.performance().getVenue())
                .performanceDate(detailView.performance().getPerformanceDate())
                .performanceTime(detailView.performance().getPerformanceTime())
                .totalSeats(detailView.performance().getTotalSeats())
                .runningTimeMinutes(detailView.performance().getRunningTimeMinutes())
                .ageLimit(detailView.performance().getAgeLimit())
                .venueAddress(detailView.performance().getVenueAddress())
                .bookingNotice(detailView.performance().getBookingNotice())
                .organizer(detailView.performance().getOrganizer())
                .host(detailView.performance().getHost())
                .seatGrades(detailView.seatGrades().stream().map(SeatGradeResponse::from).toList())
                .castMembers(detailView.castMembers().stream().map(CastMemberResponse::from).toList())
                .createdAt(detailView.item().getCreatedAt())
                .updatedAt(detailView.item().getUpdatedAt())
                .build();
    }
}
