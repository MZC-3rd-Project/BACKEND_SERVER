package com.example.stock.entity;

import com.example.core.exception.BusinessException;
import com.example.stock.exception.StockErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class StockItemTest {

    @Test
    void reserve_confirmAndCancelReservation_preserveInventoryInvariant() {
        StockItem stockItem = StockItem.create(71001L, StockItemType.ITEM_OPTION, 81001L, 10);

        stockItem.reserve(4);

        assertThat(stockItem.getTotalQuantity()).isEqualTo(10);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(6);
        assertThat(stockItem.getReservedQuantity()).isEqualTo(4);

        stockItem.confirmReservation(3);

        assertThat(stockItem.getTotalQuantity()).isEqualTo(10);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(6);
        assertThat(stockItem.getReservedQuantity()).isEqualTo(1);

        stockItem.cancelReservation(1);

        assertThat(stockItem.getTotalQuantity()).isEqualTo(10);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(7);
        assertThat(stockItem.getReservedQuantity()).isZero();
    }

    @Test
    void confirmReservation_andCancelReservation_rejectInvalidReservationQuantities() {
        StockItem stockItem = StockItem.create(71001L, StockItemType.ITEM_OPTION, 81001L, 10);
        stockItem.reserve(2);

        BusinessException confirmException = catchThrowableOfType(
                () -> stockItem.confirmReservation(0),
                BusinessException.class
        );
        BusinessException cancelException = catchThrowableOfType(
                () -> stockItem.cancelReservation(3),
                BusinessException.class
        );

        assertThat(confirmException).isNotNull();
        assertThat(confirmException.getErrorCode()).isEqualTo(StockErrorCode.INVALID_RESERVATION_STATUS);
        assertThat(cancelException).isNotNull();
        assertThat(cancelException.getErrorCode()).isEqualTo(StockErrorCode.INVALID_RESERVATION_STATUS);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(8);
        assertThat(stockItem.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    void cancelReservation_rejectsOverflowWhenCorruptedStateWouldExceedTotal() {
        StockItem stockItem = StockItem.create(71001L, StockItemType.ITEM_OPTION, 81001L, 5);
        stockItem.reserve(1);
        forceQuantities(stockItem, 5, 5, 1);

        BusinessException exception = catchThrowableOfType(
                () -> stockItem.cancelReservation(1),
                BusinessException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo(StockErrorCode.STOCK_OVERFLOW);
        assertThat(stockItem.getTotalQuantity()).isEqualTo(5);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(5);
        assertThat(stockItem.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void updateTotal_recalculatesAvailableFromReservedQuantity() {
        StockItem stockItem = StockItem.create(71001L, StockItemType.SEAT_GRADE, 81002L, 10);
        stockItem.reserve(4);

        stockItem.updateTotal(12);

        assertThat(stockItem.getTotalQuantity()).isEqualTo(12);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(8);
        assertThat(stockItem.getReservedQuantity()).isEqualTo(4);
    }

    @Test
    void updateTotal_rejectsTotalBelowReservedQuantity() {
        StockItem stockItem = StockItem.create(71001L, StockItemType.SEAT_GRADE, 81002L, 10);
        stockItem.reserve(4);

        BusinessException exception = catchThrowableOfType(
                () -> stockItem.updateTotal(3),
                BusinessException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo(StockErrorCode.STOCK_OVERFLOW);
        assertThat(stockItem.getTotalQuantity()).isEqualTo(10);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(6);
        assertThat(stockItem.getReservedQuantity()).isEqualTo(4);
    }

    @Test
    void reserve_rejectsReservationBeyondAvailableQuantity() {
        StockItem stockItem = StockItem.create(71001L, StockItemType.ITEM_OPTION, 81001L, 3);

        assertThatThrownBy(() -> stockItem.reserve(4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Insufficient stock for reservation");
    }

    private void forceQuantities(StockItem stockItem, int totalQuantity, int availableQuantity, int reservedQuantity) {
        setField(stockItem, "totalQuantity", totalQuantity);
        setField(stockItem, "availableQuantity", availableQuantity);
        setField(stockItem, "reservedQuantity", reservedQuantity);
    }

    private void setField(StockItem stockItem, String fieldName, int value) {
        try {
            Field field = StockItem.class.getDeclaredField(fieldName);
            if (!field.trySetAccessible()) {
                throw new AssertionError("Failed to access field: " + fieldName);
            }
            field.setInt(stockItem, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to set field: " + fieldName, exception);
        }
    }
}
