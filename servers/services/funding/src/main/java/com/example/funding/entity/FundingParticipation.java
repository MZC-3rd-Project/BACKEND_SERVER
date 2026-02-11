package com.example.funding.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "funding_participations",
        indexes = {
                @Index(name = "idx_participation_campaign_id", columnList = "campaignId"),
                @Index(name = "idx_participation_user_id", columnList = "userId"),
                @Index(name = "idx_participation_status", columnList = "status"),
                @Index(name = "idx_participation_order_id", columnList = "orderId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class FundingParticipation extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "seat_grade_id")
    private Long seatGradeId;

    @Column(name = "item_option_id")
    private Long itemOptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParticipationStatus status;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "payment_id")
    private Long paymentId;

    public static FundingParticipation create(Long campaignId, Long userId, Long amount,
                                               Integer quantity, Long seatGradeId,
                                               Long itemOptionId, Long orderId,
                                               Long reservationId) {
        FundingParticipation p = new FundingParticipation();
        p.campaignId = campaignId;
        p.userId = userId;
        p.amount = amount;
        p.quantity = quantity;
        p.seatGradeId = seatGradeId;
        p.itemOptionId = itemOptionId;
        p.orderId = orderId;
        p.reservationId = reservationId;
        p.status = ParticipationStatus.PENDING;
        return p;
    }

    public void confirm(Long paymentId) {
        this.status = ParticipationStatus.CONFIRMED;
        this.paymentId = paymentId;
    }

    public void refund() {
        this.status = ParticipationStatus.REFUNDED;
    }
}
