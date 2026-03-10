package com.example.funding.entity;

import com.example.core.exception.BusinessException;
import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.funding.exception.FundingErrorCode;
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

    @Column(name = "payment_id")
    private Long paymentId;

    public static FundingParticipation create(Long campaignId, Long userId, Long amount,
                                               Integer quantity, Long seatGradeId,
                                               Long itemOptionId, Long orderId) {
        return createConfirmed(campaignId, userId, amount, quantity, seatGradeId, itemOptionId, orderId, null);
    }

    public static FundingParticipation createConfirmed(Long campaignId, Long userId, Long amount,
                                                       Integer quantity, Long seatGradeId,
                                                       Long itemOptionId, Long orderId, Long paymentId) {
        return newParticipation(
                campaignId,
                userId,
                amount,
                quantity,
                seatGradeId,
                itemOptionId,
                orderId,
                ParticipationStatus.CONFIRMED,
                paymentId
        );
    }

    private static FundingParticipation newParticipation(Long campaignId, Long userId, Long amount,
                                                         Integer quantity, Long seatGradeId,
                                                         Long itemOptionId, Long orderId,
                                                         ParticipationStatus status, Long paymentId) {
        FundingParticipation p = new FundingParticipation();
        p.campaignId = campaignId;
        p.userId = userId;
        p.amount = amount;
        p.quantity = quantity;
        p.seatGradeId = seatGradeId;
        p.itemOptionId = itemOptionId;
        p.orderId = orderId;
        p.status = status;
        p.paymentId = paymentId;
        return p;
    }

    public void refund() {
        if (this.status != ParticipationStatus.CONFIRMED) {
            throw new BusinessException(FundingErrorCode.INVALID_PARTICIPATION_STATUS);
        }
        this.status = ParticipationStatus.REFUNDED;
    }
}
