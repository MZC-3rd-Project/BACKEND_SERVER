package com.example.sales.domain.checkout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReserveIntentTest {

    @Test
    void matches_returnsTrueWhenOnlyOrderAndCaseDiffer() {
        ReserveIntent left = new ReserveIntent(List.of(
                new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(930002L, 930201L),
                        "funding",
                        4401L,
                        "item_option",
                        1
                ),
                new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(930001L, 930101L),
                        " normal ",
                        null,
                        "item_option",
                        2
                )
        ));

        ReserveIntent right = new ReserveIntent(List.of(
                new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(930001L, 930101L),
                        "NORMAL",
                        null,
                        "ITEM_OPTION",
                        2
                ),
                new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(930002L, 930201L),
                        "FUNDING",
                        4401L,
                        "ITEM_OPTION",
                        1
                )
        ));

        assertThat(left.matches(right)).isTrue();
        assertThat(right.matches(left)).isTrue();
    }

    @Test
    void matches_returnsFalseWhenLineItemMeaningDiffers() {
        ReserveIntent left = new ReserveIntent(List.of(
                new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(930001L, 930101L),
                        "NORMAL",
                        null,
                        "ITEM_OPTION",
                        1
                )
        ));

        ReserveIntent right = new ReserveIntent(List.of(
                new ReserveIntent.LineItem(
                        new CheckoutLineItemKey(930001L, 930101L),
                        "NORMAL",
                        null,
                        "ITEM_OPTION",
                        2
                )
        ));

        assertThat(left.matches(right)).isFalse();
    }
}
