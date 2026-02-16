package com.example.security.context;

import com.example.contracts.http.HttpHeaderNames;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedHeaderParserTest {

    @Test
    void shouldParseValidSignedHeaders() {
        String signingKey = "0123456789abcdef";
        HmacSigner signer = new HmacSigner(signingKey);
        SignedHeaderParser parser = new SignedHeaderParser(signer, 300_000);

        long timestamp = System.currentTimeMillis();
        String roles = "ROLE_USER,ROLE_ADMIN";
        String payload = HmacSigner.buildSignaturePayload("100", roles, "nonce-1", timestamp);
        String signature = signer.sign(payload);

        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaderNames.USER_ID, "100");
        headers.put(HttpHeaderNames.USER_ROLES, roles);
        headers.put(HttpHeaderNames.NONCE, "nonce-1");
        headers.put(HttpHeaderNames.TIMESTAMP, String.valueOf(timestamp));
        headers.put(HttpHeaderNames.SIGNATURE, signature);

        AuthContext context = parser.parse(headers::get);

        assertThat(context.getUserId()).isEqualTo("100");
        assertThat(context.getRoles()).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(context.getNonce()).isEqualTo("nonce-1");
    }

    @Test
    void shouldThrowWhenTimestampIsExpired() {
        String signingKey = "0123456789abcdef";
        HmacSigner signer = new HmacSigner(signingKey);
        SignedHeaderParser parser = new SignedHeaderParser(signer, 1000);

        long oldTimestamp = System.currentTimeMillis() - 10_000;
        String roles = "ROLE_USER";
        String payload = HmacSigner.buildSignaturePayload("100", roles, "nonce-2", oldTimestamp);
        String signature = signer.sign(payload);

        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaderNames.USER_ID, "100");
        headers.put(HttpHeaderNames.USER_ROLES, roles);
        headers.put(HttpHeaderNames.NONCE, "nonce-2");
        headers.put(HttpHeaderNames.TIMESTAMP, String.valueOf(oldTimestamp));
        headers.put(HttpHeaderNames.SIGNATURE, signature);

        assertThatThrownBy(() -> parser.parse(headers::get))
                .isInstanceOf(HeaderSecurityException.class)
                .hasMessageContaining("만료");
    }
}
