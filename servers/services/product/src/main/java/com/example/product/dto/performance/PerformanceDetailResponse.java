package com.example.product.dto.performance;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.domain.item.Item;
import com.example.product.domain.performance.CastMember;
import com.example.product.domain.performance.Performance;
import com.example.product.domain.performance.SeatGrade;
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
    private String thumbnailUrl;

    @SnowflakeId
    private Long categoryId;

    @SnowflakeId
    private Long sellerId;

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
                                               List<CastMember> castMembers) {
        return PerformanceDetailResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .thumbnailUrl(item.getThumbnailUrl())
                .categoryId(item.getCategoryId())
                .sellerId(item.getSellerId())
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
