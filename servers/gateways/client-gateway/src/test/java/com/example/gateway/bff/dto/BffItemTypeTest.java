package com.example.gateway.bff.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BffItemTypeTest {

    @Test
    void fromNullable_parsesCaseInsensitiveType() {
        assertThat(BffItemType.fromNullable("product")).isEqualTo(BffItemType.PRODUCT);
        assertThat(BffItemType.fromNullable("GOODS")).isEqualTo(BffItemType.GOODS);
        assertThat(BffItemType.fromNullable(" Performance ")).isEqualTo(BffItemType.PERFORMANCE);
    }

    @Test
    void fromNullable_returnsNullWhenBlank() {
        assertThat(BffItemType.fromNullable(" ")).isNull();
        assertThat(BffItemType.fromNullable(null)).isNull();
    }
}
