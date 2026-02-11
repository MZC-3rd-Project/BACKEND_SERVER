package com.example.funding.entity;

import com.example.core.exception.BusinessException;
import com.example.data.entity.BaseEntity;
import com.example.funding.exception.FundingErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "funding_campaigns",
        indexes = {
                @Index(name = "idx_campaign_item_id", columnList = "itemId", unique = true),
                @Index(name = "idx_campaign_status", columnList = "status"),
                @Index(name = "idx_campaign_end_at", columnList = "endAt")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class FundingCampaign extends BaseEntity {

    @Id
    @com.example.core.id.jpa.SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false, unique = true)
    private Long itemId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_type", nullable = false, length = 20)
    private FundingType fundingType;

    @Column(name = "goal_amount", nullable = false)
    private Long goalAmount;

    @Column(name = "current_amount", nullable = false)
    private Long currentAmount;

    @Column(name = "goal_quantity")
    private Integer goalQuantity;

    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity;

    @Column(name = "min_amount")
    private Long minAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FundingStatus status;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    public static FundingCampaign create(Long itemId, Long sellerId, FundingType fundingType,
                                          Long goalAmount, Integer goalQuantity, Long minAmount,
                                          LocalDateTime startAt, LocalDateTime endAt) {
        FundingCampaign campaign = new FundingCampaign();
        campaign.itemId = itemId;
        campaign.sellerId = sellerId;
        campaign.fundingType = fundingType;
        campaign.goalAmount = goalAmount;
        campaign.currentAmount = 0L;
        campaign.goalQuantity = goalQuantity;
        campaign.currentQuantity = 0;
        campaign.minAmount = minAmount;
        campaign.status = FundingStatus.ACTIVE;
        campaign.startAt = startAt;
        campaign.endAt = endAt;
        return campaign;
    }

    public void changeStatus(FundingStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
    }

    public void addParticipation(Long amount, int quantity) {
        this.currentAmount += amount;
        this.currentQuantity += quantity;
    }

    public void removeParticipation(Long amount, int quantity) {
        this.currentAmount -= amount;
        this.currentQuantity -= quantity;
    }

    public boolean isGoalReached() {
        if (fundingType == FundingType.AMOUNT_BASED) {
            return currentAmount >= goalAmount;
        }
        return (goalQuantity != null && currentQuantity >= goalQuantity)
                || currentAmount >= goalAmount;
    }

    public boolean isActive() {
        return this.status == FundingStatus.ACTIVE;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.endAt);
    }

    public void validateOwnership(Long sellerId) {
        if (!this.sellerId.equals(sellerId)) {
            throw new BusinessException(FundingErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    public void update(Long goalAmount, Integer goalQuantity, Long minAmount,
                       LocalDateTime startAt, LocalDateTime endAt) {
        if (!isActive()) {
            throw new BusinessException(FundingErrorCode.CAMPAIGN_NOT_EDITABLE);
        }
        this.goalAmount = goalAmount;
        this.goalQuantity = goalQuantity;
        this.minAmount = minAmount;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
