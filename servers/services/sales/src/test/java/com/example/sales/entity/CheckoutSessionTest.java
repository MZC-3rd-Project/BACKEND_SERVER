package com.example.sales.entity;

import com.example.sales.domain.checkout.CheckoutLineItemKey;
import com.example.sales.domain.checkout.QuoteSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutSessionTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 3, 14, 12, 0);

    @Test
    void ensureQuoteSnapshot_marksReservedSessionQuoted_andPersistsQuotedLineItems() {
        CheckoutSession session = reservedSession();
        CheckoutSessionLineItem lineItem = reservedLineItem(1, 91001L, 92001L, 2);
        session.addLineItem(lineItem);

        LocalDateTime quotedAt = LocalDateTime.of(2026, 3, 14, 12, 5);

        session.ensureQuoteSnapshot(snapshot(
                quotedAt,
                quotedLineItem(91001L, 92001L, 2, 2_200L)
        ));

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.QUOTED);
        assertThat(session.getQuotedAt()).isEqualTo(quotedAt);
        assertThat(session.hasPersistedQuoteSnapshot()).isTrue();
        assertThat(lineItem.getItemType()).isEqualTo("ITEM_OPTION");
        assertThat(lineItem.getTitle()).isEqualTo("Premium Package");
        assertThat(lineItem.getReferenceName()).isEqualTo("Option A");
        assertThat(lineItem.getBaseUnitPrice()).isEqualTo(1_200L);
        assertThat(lineItem.getFinalUnitPrice()).isEqualTo(1_100L);
        assertThat(lineItem.getLineAmount()).isEqualTo(2_200L);
    }

    @Test
    void hasPersistedQuoteSnapshot_requiresAllLineItems_andTurnsFalseAfterSubmitting() {
        CheckoutSession session = reservedSession();
        CheckoutSessionLineItem first = reservedLineItem(1, 91001L, 92001L, 1);
        CheckoutSessionLineItem second = reservedLineItem(2, 91002L, 92002L, 1);
        session.addLineItem(first);
        session.addLineItem(second);

        session.ensureQuoteSnapshot(snapshot(
                LocalDateTime.of(2026, 3, 14, 12, 10),
                quotedLineItem(91001L, 92001L, 1, 1_100L)
        ));

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.QUOTED);
        assertThat(session.hasPersistedQuoteSnapshot()).isFalse();

        session.applyQuoteSnapshot(snapshot(
                LocalDateTime.of(2026, 3, 14, 12, 15),
                quotedLineItem(91001L, 92001L, 1, 1_100L),
                quotedLineItem(91002L, 92002L, 1, 2_100L)
        ));

        assertThat(session.hasPersistedQuoteSnapshot()).isTrue();

        session.markSubmitting();

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.SUBMITTING);
        assertThat(session.getSubmitRequestedAt()).isNotNull();
        assertThat(session.hasPersistedQuoteSnapshot()).isFalse();
    }

    @Test
    void markFailed_normalizesBlankErrorsToNull() {
        CheckoutSession session = reservedSession();

        session.markFailed("   ", "\n\t");

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.FAILED);
        assertThat(session.getLastErrorCode()).isNull();
        assertThat(session.getLastErrorMessage()).isNull();
    }

    @Test
    void markFailed_truncatesOverlongErrorValues() {
        CheckoutSession session = reservedSession();
        String longCode = "E".repeat(70);
        String longMessage = "M".repeat(620);

        session.markFailed(longCode, longMessage);

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.FAILED);
        assertThat(session.getLastErrorCode()).hasSize(50).isEqualTo(longCode.substring(0, 50));
        assertThat(session.getLastErrorMessage()).hasSize(500).isEqualTo(longMessage.substring(0, 500));
    }

    @Test
    void markSubmitting_clearsLastFailureError_whenRetryStarts() {
        CheckoutSession session = reservedSession();
        session.markFailed("QUOTE-FAILED", "temporary failure");

        session.markSubmitting();

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.SUBMITTING);
        assertThat(session.getSubmitRequestedAt()).isNotNull();
        assertThat(session.getLastErrorCode()).isNull();
        assertThat(session.getLastErrorMessage()).isNull();
    }

    private CheckoutSession reservedSession() {
        return CheckoutSession.createReserved(99001L, 88001L, "checkout-key", EXPIRES_AT);
    }

    private CheckoutSessionLineItem reservedLineItem(int lineNo, Long itemId, Long referenceId, int quantity) {
        return CheckoutSessionLineItem.createReserved(
                lineNo,
                " normal ",
                4401L,
                itemId,
                " item_option ",
                referenceId,
                quantity
        );
    }

    private QuoteSnapshot snapshot(LocalDateTime quotedAt, QuoteSnapshot.QuotedLineItem... lineItems) {
        return new QuoteSnapshot(
                99001L,
                EXPIRES_AT,
                quotedAt,
                4_300L,
                List.of(lineItems)
        );
    }

    private QuoteSnapshot.QuotedLineItem quotedLineItem(Long itemId, Long referenceId, int quantity, long lineAmount) {
        return new QuoteSnapshot.QuotedLineItem(
                new CheckoutLineItemKey(itemId, referenceId),
                " ITEM_OPTION ",
                " Premium Package ",
                5501L,
                6601L,
                " Option A ",
                " ITEM_OPTION ",
                quantity,
                1_200L,
                1_100L,
                lineAmount
        );
    }
}
