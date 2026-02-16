package com.example.security.crypto.pii;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingUtilsTest {

    @Test
    void shouldMaskEmailAndPhonePatternsInLogMessage() {
        String raw = "email=tester@example.com, phone=010-1234-5678";

        String masked = PiiMaskingUtils.maskLogMessage(raw);

        assertThat(masked).doesNotContain("tester@example.com");
        assertThat(masked).doesNotContain("010-1234-5678");
        assertThat(masked).contains("***");
    }
}
