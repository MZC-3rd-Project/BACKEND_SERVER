package com.example.contracts.http;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HttpHeaderNamesTest {

    @Test
    void shouldExposeStableHeaderNames() {
        assertThat(HttpHeaderNames.USER_ID).isEqualTo("X-User-Id");
        assertThat(HttpHeaderNames.USER_ROLES).isEqualTo("X-User-Roles");
        assertThat(HttpHeaderNames.NONCE).isEqualTo("X-Nonce");
        assertThat(HttpHeaderNames.TIMESTAMP).isEqualTo("X-Timestamp");
        assertThat(HttpHeaderNames.SIGNATURE).isEqualTo("X-Signature");
        assertThat(HttpHeaderNames.GATEWAY_CONTEXT).isEqualTo("X-Gateway-Context");
        assertThat(HttpHeaderNames.GATEWAY_AUTH).isEqualTo("X-Gateway-Auth");
    }

    @Test
    void shouldNotContainDuplicateHeaderNames() {
        Set<String> headers = Set.of(
                HttpHeaderNames.USER_ID,
                HttpHeaderNames.USER_ROLES,
                HttpHeaderNames.NONCE,
                HttpHeaderNames.TIMESTAMP,
                HttpHeaderNames.SIGNATURE,
                HttpHeaderNames.GATEWAY_CONTEXT,
                HttpHeaderNames.GATEWAY_AUTH
        );

        assertThat(headers).hasSize(7);
    }
}
