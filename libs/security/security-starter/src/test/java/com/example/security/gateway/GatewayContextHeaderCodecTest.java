package com.example.security.gateway;

import com.example.security.signature.HeaderSecurityException;
import com.example.security.signature.HmacSigner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayContextHeaderCodecTest {

    @Test
    void encodeSignedAndDecode_roundTrip() {
        HmacSigner signer = new HmacSigner("gateway-sign-key");
        String token = GatewayContextHeaderCodec.encodeSigned(
                "10",
                "ROLE_USER,ROLE_ADMIN",
                "nonce-1",
                123456789L,
                signer
        );

        GatewayContextHeaderCodec.ParsedGatewayContext parsed = GatewayContextHeaderCodec.decode(token);

        assertThat(parsed.userId()).isEqualTo("10");
        assertThat(parsed.roles()).isEqualTo("ROLE_USER,ROLE_ADMIN");
        assertThat(parsed.nonce()).isEqualTo("nonce-1");
        assertThat(parsed.timestamp()).isEqualTo(123456789L);
        assertThat(parsed.signature()).isNotBlank();
    }

    @Test
    void decode_throwsWhenTokenMalformed() {
        assertThatThrownBy(() -> GatewayContextHeaderCodec.decode("bad-token"))
                .isInstanceOf(HeaderSecurityException.class)
                .hasMessageContaining("gateway context");
    }
}
