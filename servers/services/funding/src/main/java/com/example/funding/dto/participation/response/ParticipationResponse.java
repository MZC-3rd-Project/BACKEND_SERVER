package com.example.funding.dto.participation.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.funding.entity.FundingParticipation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ParticipationResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long campaignId;

    @SnowflakeId
    private Long userId;

    private Long amount;
    private Integer quantity;

    @SnowflakeId
    private Long seatGradeId;

    @SnowflakeId
    private Long itemOptionId;

    private String status;

    @SnowflakeId
    private Long orderId;

    @SnowflakeId
    private Long reservationId;

    @SnowflakeId
    private Long paymentId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ParticipationResponse from(FundingParticipation p) {
        return ParticipationResponse.builder()
                .id(p.getId())
                .campaignId(p.getCampaignId())
                .userId(p.getUserId())
                .amount(p.getAmount())
                .quantity(p.getQuantity())
                .seatGradeId(p.getSeatGradeId())
                .itemOptionId(p.getItemOptionId())
                .status(p.getStatus().name())
                .orderId(p.getOrderId())
                .reservationId(p.getReservationId())
                .paymentId(p.getPaymentId())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
