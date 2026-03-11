package com.example.stock.entity;

import com.example.core.exception.BusinessException;
import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.stock.exception.StockErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "order_reserve_idempotencies",
        indexes = {
                @Index(name = "uk_order_reserve_idempotency_user_key", columnList = "user_id, idempotency_key", unique = true),
                @Index(name = "uk_order_reserve_idempotency_order_id", columnList = "order_id", unique = true)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class OrderReserveIdempotency extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_signature", nullable = false, columnDefinition = "TEXT")
    private String requestSignature;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static OrderReserveIdempotency create(
            Long userId,
            String idempotencyKey,
            String requestSignature,
            Long orderId,
            LocalDateTime expiresAt
    ) {
        OrderReserveIdempotency idempotency = new OrderReserveIdempotency();
        idempotency.userId = userId;
        idempotency.idempotencyKey = idempotencyKey;
        idempotency.requestSignature = requestSignature;
        idempotency.orderId = orderId;
        idempotency.expiresAt = expiresAt;
        return idempotency;
    }

    public void ensureSameRequestSignature(String requestSignature) {
        if (!Objects.equals(this.requestSignature, requestSignature)) {
            throw new BusinessException(StockErrorCode.ORDER_RESERVE_IDEMPOTENCY_CONFLICT);
        }
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
