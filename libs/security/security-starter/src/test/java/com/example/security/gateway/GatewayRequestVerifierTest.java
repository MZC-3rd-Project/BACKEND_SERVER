package com.example.security.gateway;

import com.example.contracts.http.HttpHeaderNames;
import com.example.security.signature.HmacSigner;
import com.example.security.signature.SignedHeaderParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequestVerifierTest {

    @Test
    void validateGatewayAuth_returnsFalseWhenTokenMissingAndNoSignedParser() {
        TestGatewaySecurityProperties properties = new TestGatewaySecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("");

        GatewayRequestVerifier verifier = new GatewayRequestVerifier(properties, null);

        assertThat(verifier.validateGatewayAuth(new MockHttpServletRequest())).isFalse();
    }

    @Test
    void validateGatewayAuth_returnsTrueWhenTokenMatches() {
        TestGatewaySecurityProperties properties = new TestGatewaySecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("secret-token");

        GatewayRequestVerifier verifier = new GatewayRequestVerifier(properties, null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaderNames.GATEWAY_AUTH, "secret-token");

        assertThat(verifier.validateGatewayAuth(request)).isTrue();
    }

    @Test
    void validateUserContext_returnsFalseWhenRequiredAndHeaderMissing() {
        TestGatewaySecurityProperties properties = new TestGatewaySecurityProperties();
        GatewayRequestVerifier verifier = new GatewayRequestVerifier(properties, null);

        assertThat(verifier.validateUserContext(new MockHttpServletRequest())).isFalse();
    }

    @Test
    void validateOptionalUserContext_returnsTrueWhenHeadersMissing() {
        TestGatewaySecurityProperties properties = new TestGatewaySecurityProperties();
        GatewayRequestVerifier verifier = new GatewayRequestVerifier(properties, null);

        assertThat(verifier.validateOptionalUserContext(new MockHttpServletRequest())).isTrue();
    }

    @Test
    void validateOptionalUserContext_returnsFalseWhenGatewayContextInvalid() {
        TestGatewaySecurityProperties properties = new TestGatewaySecurityProperties();
        SignedHeaderParser parser = new SignedHeaderParser(new HmacSigner("gateway-sign-key"), 300_000);
        GatewayRequestVerifier verifier = new GatewayRequestVerifier(properties, parser);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaderNames.GATEWAY_CONTEXT, "invalid-token");

        assertThat(verifier.validateOptionalUserContext(request)).isFalse();
    }

    @Test
    void validateUserContext_returnsTrueWhenGatewayContextHeaderIsValid() {
        TestGatewaySecurityProperties properties = new TestGatewaySecurityProperties();
        HmacSigner signer = new HmacSigner("gateway-sign-key");
        SignedHeaderParser parser = new SignedHeaderParser(signer, 300_000);
        GatewayRequestVerifier verifier = new GatewayRequestVerifier(properties, parser);

        long timestamp = System.currentTimeMillis();
        String token = GatewayContextHeaderCodec.encodeSigned(
                "10",
                "ROLE_USER",
                "nonce-2",
                timestamp,
                signer
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaderNames.GATEWAY_CONTEXT, token);

        assertThat(verifier.validateUserContext(request)).isTrue();
    }

    private static final class TestGatewaySecurityProperties implements GatewayHeaderValidationProperties {
        private boolean gatewayAuthEnabled = true;
        private String internalAuthToken = "";

        @Override
        public boolean isGatewayAuthEnabled() {
            return gatewayAuthEnabled;
        }

        public void setGatewayAuthEnabled(boolean gatewayAuthEnabled) {
            this.gatewayAuthEnabled = gatewayAuthEnabled;
        }

        @Override
        public String getInternalAuthHeader() {
            return HttpHeaderNames.GATEWAY_AUTH;
        }

        @Override
        public String getInternalAuthToken() {
            return internalAuthToken;
        }

        public void setInternalAuthToken(String internalAuthToken) {
            this.internalAuthToken = internalAuthToken;
        }

        @Override
        public String getUserIdHeader() {
            return HttpHeaderNames.USER_ID;
        }
    }
}
