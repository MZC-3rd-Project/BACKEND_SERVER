package com.example.product.domain.item;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "item_status_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemStatusHistory extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private ItemStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private ItemStatus newStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    public static ItemStatusHistory create(Long itemId, ItemStatus previousStatus,
                                           ItemStatus newStatus, String reason, Long changedBy) {
        ItemStatusHistory h = new ItemStatusHistory();
        h.itemId = itemId;
        h.previousStatus = previousStatus;
        h.newStatus = newStatus;
        h.reason = reason;
        h.changedBy = changedBy;
        return h;
    }
}
