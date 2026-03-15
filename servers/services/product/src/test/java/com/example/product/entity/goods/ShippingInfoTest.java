package com.example.product.entity.goods;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingInfoTest {

    @Test
    void create_withLegacyFactory_keepsExtendedMetadataNull() {
        ShippingInfo shippingInfo = ShippingInfo.create(1L, 3000L, 50000L, 2, "exchange within 7 days");

        assertThat(shippingInfo.getCarrier()).isNull();
        assertThat(shippingInfo.getShipFrom()).isNull();
        assertThat(shippingInfo.getReturnAddress()).isNull();
        assertThat(shippingInfo.getReturnShippingFee()).isNull();
        assertThat(shippingInfo.getExchangeShippingFee()).isNull();
        assertThat(shippingInfo.getShippingNotice()).isNull();
    }

    @Test
    void create_withExtendedMetadata_setsAllDetailFields() {
        ShippingInfo shippingInfo = ShippingInfo.create(
                1L,
                3000L,
                50000L,
                2,
                "exchange within 7 days",
                "CJ Logistics",
                "Seoul Mapo-gu",
                "Seoul Jung-gu",
                3500L,
                7000L,
                "Remote areas may incur extra fees."
        );

        assertThat(shippingInfo.getCarrier()).isEqualTo("CJ Logistics");
        assertThat(shippingInfo.getShipFrom()).isEqualTo("Seoul Mapo-gu");
        assertThat(shippingInfo.getReturnAddress()).isEqualTo("Seoul Jung-gu");
        assertThat(shippingInfo.getReturnShippingFee()).isEqualTo(3500L);
        assertThat(shippingInfo.getExchangeShippingFee()).isEqualTo(7000L);
        assertThat(shippingInfo.getShippingNotice()).isEqualTo("Remote areas may incur extra fees.");
    }

    @Test
    void update_withExtendedMetadata_replacesAllDetailFields() {
        ShippingInfo shippingInfo = ShippingInfo.create(1L, 3000L, 50000L, 2, "exchange within 7 days");

        shippingInfo.update(
                4000L,
                70000L,
                3,
                "no return after use",
                "Hanjin",
                "Busan",
                "Incheon",
                4000L,
                8000L,
                "Island delivery is limited."
        );

        assertThat(shippingInfo.getShippingFee()).isEqualTo(4000L);
        assertThat(shippingInfo.getFreeShippingThreshold()).isEqualTo(70000L);
        assertThat(shippingInfo.getEstimatedDays()).isEqualTo(3);
        assertThat(shippingInfo.getReturnPolicy()).isEqualTo("no return after use");
        assertThat(shippingInfo.getCarrier()).isEqualTo("Hanjin");
        assertThat(shippingInfo.getShipFrom()).isEqualTo("Busan");
        assertThat(shippingInfo.getReturnAddress()).isEqualTo("Incheon");
        assertThat(shippingInfo.getReturnShippingFee()).isEqualTo(4000L);
        assertThat(shippingInfo.getExchangeShippingFee()).isEqualTo(8000L);
        assertThat(shippingInfo.getShippingNotice()).isEqualTo("Island delivery is limited.");
    }

    @Test
    void update_withLegacySignature_preservesExtendedMetadata() {
        ShippingInfo shippingInfo = ShippingInfo.create(
                1L,
                3000L,
                50000L,
                2,
                "exchange within 7 days",
                "CJ Logistics",
                "Seoul Mapo-gu",
                "Seoul Jung-gu",
                3500L,
                7000L,
                "Remote areas may incur extra fees."
        );

        shippingInfo.update(4000L, 70000L, 3, "no return after use");

        assertThat(shippingInfo.getCarrier()).isEqualTo("CJ Logistics");
        assertThat(shippingInfo.getShipFrom()).isEqualTo("Seoul Mapo-gu");
        assertThat(shippingInfo.getReturnAddress()).isEqualTo("Seoul Jung-gu");
        assertThat(shippingInfo.getReturnShippingFee()).isEqualTo(3500L);
        assertThat(shippingInfo.getExchangeShippingFee()).isEqualTo(7000L);
        assertThat(shippingInfo.getShippingNotice()).isEqualTo("Remote areas may incur extra fees.");
    }
}
