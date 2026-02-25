package com.example.product.dto.performance.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemDetailSectionResponse;
import com.example.product.entity.performance.CastMember;
import com.example.product.entity.item.Item;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import lombok.Builder;
import lombok.Getter;

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
    private ItemImagesResponse images;

    @SnowflakeId
    private Long categoryId;

    @SnowflakeId
    private Long sellerId;

    @SnowflakeId
    private Long storeId;

    private List<String> tags;
    private List<String> features;
    private List<ItemDetailSectionResponse> detailSections;

    private String venue;
    private LocalDate performanceDate;
    private LocalTime performanceTime;
    private Integer totalSeats;

    private List<SeatGradeResponse> seatGrades;
    private List<CastMemberResponse> castMembers;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PerformanceDetailResponse of(Item item, Performance perf,
                                               List<SeatGrade> seatGrades,
                                               List<CastMember> castMembers,
                                               ItemContentSnapshot content,
                                               List<ItemImage> images) {
        ItemContentSnapshot safeContent = content != null ? content : ItemContentSnapshot.empty();
        return PerformanceDetailResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .images(ItemImagesResponse.from(images, item.getThumbnailMediaId()))
                .categoryId(item.getCategoryId())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .tags(safeContent.tags())
                .features(safeContent.features())
                .detailSections(safeContent.detailSections())
                .venue(perf.getVenue())
                .performanceDate(perf.getPerformanceDate())
                .performanceTime(perf.getPerformanceTime())
                .totalSeats(perf.getTotalSeats())
                .seatGrades(seatGrades.stream().map(SeatGradeResponse::from).toList())
                .castMembers(castMembers.stream().map(CastMemberResponse::from).toList())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
