package com.example.product.dto.performance.request;

import com.example.product.dto.item.request.ItemDetailSectionRequest;
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

    @Valid
    private List<SeatGradeRequest> seatGrades;

    @Valid
    private List<CastMemberRequest> castMembers;
}
