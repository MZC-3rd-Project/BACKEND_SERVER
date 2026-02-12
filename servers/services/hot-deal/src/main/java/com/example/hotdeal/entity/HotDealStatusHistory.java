package com.example.hotdeal.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hot_deal_status_histories",
        indexes = {
                @Index(name = "idx_history_hot_deal_id", columnList = "hotDealId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotDealStatusHistory extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "hot_deal_id", nullable = false)
    private Long hotDealId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private HotDealStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private HotDealStatus toStatus;

    @Column(name = "reason")
    private String reason;

    public static HotDealStatusHistory create(Long hotDealId, HotDealStatus fromStatus,
                                               HotDealStatus toStatus, String reason) {
        HotDealStatusHistory history = new HotDealStatusHistory();
        history.hotDealId = hotDealId;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.reason = reason;
        return history;
    }
}
