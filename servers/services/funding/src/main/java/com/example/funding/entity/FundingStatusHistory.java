package com.example.funding.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "funding_status_histories",
        indexes = {
                @Index(name = "idx_funding_status_history_campaign_id", columnList = "campaignId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingStatusHistory extends BaseEntity {

    @Id
    @com.example.core.id.jpa.SnowflakeGenerated
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private FundingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private FundingStatus newStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    public static FundingStatusHistory create(Long campaignId, FundingStatus previousStatus,
                                               FundingStatus newStatus, String reason) {
        FundingStatusHistory history = new FundingStatusHistory();
        history.campaignId = campaignId;
        history.previousStatus = previousStatus;
        history.newStatus = newStatus;
        history.reason = reason;
        return history;
    }
}
