package com.example.cart.domain;

import com.example.cart.exception.CartErrorCode;
import com.example.core.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    @Test
    void addOrMerge_sameIdentity_accumulatesQuantity() {
        Instant now = Instant.parse("2026-03-13T01:00:00Z");
        long expiresAt = now.plusSeconds(3600).getEpochSecond();
        Cart cart = Cart.of(1L, List.of(
                line(1L, 11L, "NORMAL", null, 2, true, now, expiresAt)
        ));

        CartLine updated = cart.addOrMerge(
                line(1L, 11L, "NORMAL", null, 3, true, now, expiresAt),
                now,
                expiresAt
        );

        assertThat(updated.getQuantity()).isEqualTo(5);
        assertThat(cart.lines()).hasSize(1);
    }

    @Test
    void changeSelection_updatesRequestedLines() {
        Instant now = Instant.parse("2026-03-13T01:00:00Z");
        long expiresAt = now.plusSeconds(3600).getEpochSecond();
        Cart cart = Cart.of(1L, List.of(
                line(1L, 11L, "NORMAL", null, 2, true, now, expiresAt),
                line(2L, 22L, "FUNDING", 99L, 1, true, now, expiresAt)
        ));

        cart.changeSelections(
                java.util.Map.of(
                        CartLineIdentity.of(1L, 11L, "NORMAL", null), false,
                        CartLineIdentity.of(2L, 22L, "FUNDING", 99L), false
                ),
                now,
                expiresAt
        );

        assertThat(cart.lines()).allMatch(line -> !line.isSelected());
    }

    @Test
    void selectedLines_withoutSelected_throwsBusinessException() {
        Instant now = Instant.parse("2026-03-13T01:00:00Z");
        long expiresAt = now.plusSeconds(3600).getEpochSecond();
        Cart cart = Cart.of(1L, List.of(
                line(1L, 11L, "NORMAL", null, 2, false, now, expiresAt)
        ));

        assertThatThrownBy(cart::selectedLines)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CartErrorCode.EMPTY_SELECTED_ITEMS);
    }

    private CartLine line(
            Long itemId,
            Long referenceId,
            String channelType,
            Long channelRefId,
            int quantity,
            boolean selected,
            Instant now,
            long expiresAtEpoch
    ) {
        return CartLine.create(
                CartLineIdentity.of(itemId, referenceId, channelType, channelRefId),
                "ITEM_OPTION",
                quantity,
                selected,
                10L,
                "item-" + itemId,
                null,
                "store",
                1000L,
                "ON_SALE",
                now,
                now,
                expiresAtEpoch
        );
    }
}
