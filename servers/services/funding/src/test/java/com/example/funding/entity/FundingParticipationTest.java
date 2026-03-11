package com.example.funding.entity;

import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundingParticipationTest {

    @Test
    void createConfirmed_setsConfirmedStatus() {
        FundingParticipation participation = FundingParticipation.createConfirmed(
                10L,
                20L,
                30_000L,
                2,
                101L,
                null,
                999L,
                null
        );

        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(participation.getOrderId()).isEqualTo(999L);
        assertThat(participation.getQuantity()).isEqualTo(2);
    }

    @Test
    void refund_changesConfirmedParticipationToRefunded() {
        FundingParticipation participation = FundingParticipation.createConfirmed(
                10L,
                20L,
                30_000L,
                2,
                101L,
                null,
                999L,
                null
        );

        participation.refund();

        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.REFUNDED);
    }

    @Test
    void refund_rejectsAlreadyRefundedParticipation() {
        FundingParticipation participation = FundingParticipation.createConfirmed(
                10L,
                20L,
                30_000L,
                2,
                101L,
                null,
                999L,
                null
        );
        participation.refund();

        assertThatThrownBy(participation::refund)
                .isInstanceOf(BusinessException.class);
    }
}
