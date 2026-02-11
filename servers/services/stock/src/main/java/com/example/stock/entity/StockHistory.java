package com.example.stock.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_histories", indexes = {
        @Index(name = "idx_history_stock_item", columnList = "stockItemId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistory extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(nullable = false)
    private Long stockItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeType changeType;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 500)
    private String reason;

    private Long reservationId;

    public static StockHistory create(Long stockItemId, ChangeType changeType, int quantity, String reason, Long reservationId) {
        StockHistory history = new StockHistory();
        history.stockItemId = stockItemId;
        history.changeType = changeType;
        history.quantity = quantity;
        history.reason = reason;
        history.reservationId = reservationId;
        return history;
    }

    public static StockHistory create(Long stockItemId, ChangeType changeType, int quantity, String reason) {
        return create(stockItemId, changeType, quantity, reason, null);
    }
}
