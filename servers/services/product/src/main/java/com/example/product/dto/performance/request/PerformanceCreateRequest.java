package com.example.product.dto.performance.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class PerformanceCreateRequest {

    @NotBlank(message = "공연 제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다")
    private String title;

    private String description;

    @NotNull(message = "가격은 필수입니다")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    private Long price;

    private Long categoryId;

    private String thumbnailUrl;

    @NotBlank(message = "공연장소는 필수입니다")
    private String venue;

    @NotNull(message = "공연 날짜는 필수입니다")
    private LocalDate performanceDate;

    @NotNull(message = "공연 시간은 필수입니다")
    private LocalTime performanceTime;

    @Min(value = 1, message = "총 좌석 수는 1 이상이어야 합니다")
    private int totalSeats;

    @NotEmpty(message = "좌석 등급은 최소 1개 이상 필요합니다")
    @Valid
    private List<SeatGradeRequest> seatGrades;

    @Valid
    private List<CastMemberRequest> castMembers;
}
