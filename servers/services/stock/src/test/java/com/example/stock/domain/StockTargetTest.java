package com.example.stock.domain;

import com.example.stock.entity.StockItemType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTargetTest {

    @Test
    void of_createsTypedStockTarget() {
        StockTarget stockTarget = StockTarget.of(StockItemType.ITEM_OPTION, 501L);

        assertThat(stockTarget.stockItemType()).isEqualTo(StockItemType.ITEM_OPTION);
        assertThat(stockTarget.referenceId()).isEqualTo(501L);
    }

    @Test
    void constructor_rejectsNullStockItemType() {
        assertThatThrownBy(() -> new StockTarget(null, 501L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stockItemType must not be null");
    }

    @Test
    void constructor_rejectsNullReferenceId() {
        assertThatThrownBy(() -> new StockTarget(StockItemType.ITEM_OPTION, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("referenceId must not be null");
    }
}
