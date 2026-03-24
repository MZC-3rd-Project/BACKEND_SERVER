package com.example.product.dto.performance.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemDetailSectionResponse;
import com.example.product.dto.item.response.ItemPriceMetaResponse;
import com.example.product.entity.performance.CastMember;
import com.example.product.entity.item.Item;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class PerformanceDetailResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private String description;
    private Long price;
    private String status;
    private String itemType;
    private ItemImagesResponse images;
    private BigDecimal averageRating;
    private Long reviewCount;

    @SnowflakeId
    private Long categoryId;
    private String categoryName;
    private List<String> categoryPath;

    @SnowflakeId
    private Long sellerId;

    @SnowflakeId
    private Long storeId;

    private List<String> tags;
    private List<String> features;
    private List<ItemDetailSectionResponse> detailSections;
    private ItemPriceMetaResponse priceMeta;

    private String venue;
    private LocalDate performanceDate;
    private LocalTime performanceTime;
    private Integer totalSeats;
    private Integer runningTimeMinutes;
    private String ageLimit;
    private String venueAddress;
    private String bookingNotice;
    private String organizer;
    private String host;

    private List<SeatGradeResponse> seatGrades;
    private List<CastMemberResponse> castMembers;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PerformanceDetailResponse of(Item item, Performance perf,
                                               List<SeatGrade> seatGrades,
                                               List<CastMember> castMembers,
                                               ItemContentSnapshot content,
                                               List<ItemImage> images) {
        return of(item, perf, seatGrades, castMembers, null, List.of(), content, images);
    }

    public static PerformanceDetailResponse of(Item item, Performance perf,
                                               List<SeatGrade> seatGrades,
                                               List<CastMember> castMembers,
                                               String categoryName, List<String> categoryPath,
                                               ItemContentSnapshot content,
                                               List<ItemImage> images) {
        ItemContentSnapshot safeContent = content != null ? content : ItemContentSnapshot.empty();
        List<String> safeCategoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);
        return PerformanceDetailResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .itemType(item.getItemType().name())
                .images(ItemImagesResponse.from(images, item.getThumbnailMediaId()))
                .averageRating(item.getAverageRating())
                .reviewCount(item.getReviewCount())
                .categoryId(item.getCategoryId())
                .categoryName(categoryName)
                .categoryPath(safeCategoryPath)
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .tags(safeContent.tags())
                .features(safeContent.features())
                .detailSections(safeContent.detailSections())
                .priceMeta(ItemPriceMetaResponse.fromSeatGrades(item.getPrice(), seatGrades))
                .venue(perf.getVenue())
                .performanceDate(perf.getPerformanceDate())
                .performanceTime(perf.getPerformanceTime())
                .totalSeats(perf.getTotalSeats())
                .runningTimeMinutes(perf.getRunningTimeMinutes())
                .ageLimit(perf.getAgeLimit())
                .venueAddress(perf.getVenueAddress())
                .bookingNotice(perf.getBookingNotice())
                .organizer(perf.getOrganizer())
                .host(perf.getHost())
                .seatGrades(seatGrades.stream().map(SeatGradeResponse::from).toList())
                .castMembers(castMembers.stream().map(CastMemberResponse::from).toList())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
