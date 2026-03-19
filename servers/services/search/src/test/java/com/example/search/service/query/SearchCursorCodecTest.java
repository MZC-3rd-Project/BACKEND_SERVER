package com.example.search.service.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorCodecTest {

    @Test
    void encodeAndDecodeOffset_roundTrips() {
        String cursor = SearchCursorCodec.encodeOffset(40);

        assertThat(SearchCursorCodec.decodeOffset(cursor)).isEqualTo(40);
    }

    @Test
    void decodeOffset_returnsZeroWhenCursorMissing() {
        assertThat(SearchCursorCodec.decodeOffset(null)).isZero();
        assertThat(SearchCursorCodec.decodeOffset("")).isZero();
    }

    @Test
    void decodeOffset_rejectsInvalidValue() {
        assertThatThrownBy(() -> SearchCursorCodec.decodeOffset("invalid-cursor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");
    }
}
