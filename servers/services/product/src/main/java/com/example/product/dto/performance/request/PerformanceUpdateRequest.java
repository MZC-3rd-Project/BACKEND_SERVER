package com.example.product.dto.performance.request;

import com.example.product.dto.item.request.ItemDetailSectionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class PerformanceUpdateRequest {

    @Size(max = 200, message = "제목은 200자 이하여야 합니다")
    private String title;

    private String description;

    @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    private Long price;

    private Long categoryId;

    @Positive(message = "썸네일 미디어 ID는 양수여야 합니다")
    private Long thumbnailMediaId;

    private Boolean clearThumbnail;

    private List<@Size(max = 50) String> tags;

    private List<@Size(max = 200) String> features;

    @Valid
    private List<ItemDetailSectionRequest> detailSections;

    @Size(max = 200, message = "공연 장소는 200자 이하여야 합니다")
    private String venue;

    private LocalDate performanceDate;

    private LocalTime performanceTime;

    @Min(value = 1, message = "총 좌석 수는 1 이상이어야 합니다")
    private Integer totalSeats;

    @Min(value = 1, message = "공연 시간은 1분 이상이어야 합니다")
    @Schema(description = "공연 러닝타임(분)", example = "130")
    private Integer runningTimeMinutes;

    @Size(max = 50, message = "관람 등급은 50자 이하여야 합니다")
    @Schema(description = "관람 등급", example = "15세 이상 관람가")
    private String ageLimit;

    @Size(max = 255, message = "공연장 주소는 255자 이하여야 합니다")
    @Schema(description = "공연장 상세 주소", example = "서울특별시 송파구 올림픽로 424")
    private String venueAddress;

    @Schema(description = "예매 및 관람 유의사항", example = "공연 시작 후 입장이 제한될 수 있습니다.")
    private String bookingNotice;

    @Size(max = 100, message = "주최 정보는 100자 이하여야 합니다")
    @Schema(description = "주최", example = "돈모아 라이브")
    private String organizer;

    @Size(max = 100, message = "주관 정보는 100자 이하여야 합니다")
    @Schema(description = "주관", example = "돈모아")
    private String host;

    @Valid
    private List<SeatGradeRequest> seatGrades;

    @Valid
    private List<CastMemberRequest> castMembers;
}
