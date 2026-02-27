package com.example.search.service.query;

import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorCodecTest {

    private final SearchCursorCodec codec = new SearchCursorCodec();

    @Test
    void encodeAndDecode_roundTrip() {
        List<Object> sortValues = List.of(123L, "abc", 4.5);

        String cursor = codec.encode(sortValues);
        List<Object> decoded = codec.decode(cursor);

        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(0)).isEqualTo(123);
        assertThat(decoded.get(1)).isEqualTo("abc");
    }

    @Test
    void decode_invalidCursor_throwsBusinessException() {
        assertThatThrownBy(() -> codec.decode("%%%invalid"))
                .isInstanceOf(BusinessException.class);
    }
}
