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

@Entity
@Table(name = "stock_reservations", indexes = {
        @Index(name = "idx_reservation_stock_item", columnList = "stockItemId"),
        @Index(name = "idx_reservation_status_expired", columnList = "status, expiredAt")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(nullable = false)
    private Long stockItemId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    public static StockReservation create(Long stockItemId, Long userId, int quantity, int ttlMinutes) {
        StockReservation reservation = new StockReservation();
        reservation.stockItemId = stockItemId;
        reservation.userId = userId;
        reservation.quantity = quantity;
        reservation.status = ReservationStatus.RESERVED;
        reservation.expiredAt = LocalDateTime.now().plusMinutes(ttlMinutes);
        return reservation;
    }

    public void confirm() {
        validateStatus(ReservationStatus.RESERVED);
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        validateStatus(ReservationStatus.RESERVED);
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        validateStatus(ReservationStatus.RESERVED);
        this.status = ReservationStatus.EXPIRED;
    }

    public boolean isExpired() {
        return this.status == ReservationStatus.RESERVED && LocalDateTime.now().isAfter(this.expiredAt);
    }

    private void validateStatus(ReservationStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(StockErrorCode.INVALID_RESERVATION_STATUS);
        }
    }
}
