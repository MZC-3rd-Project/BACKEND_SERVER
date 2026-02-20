package com.example.chat.config;

import com.example.contracts.http.HttpHeaderNames;
import com.example.security.context.HmacSigner;
import com.example.security.context.SignedHeaderParser;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHeaderValidationFilterTest {

    @Test
    void doFilter_skipsActuatorWithoutHeaders() throws ServletException, IOException {
        ChatSecurityProperties properties = new ChatSecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("secret-token");

        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_rejectsWhenGatewayTokenInvalid() throws ServletException, IOException {
        ChatSecurityProperties properties = new ChatSecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("secret-token");

        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/rooms");
        request.addHeader(HttpHeaderNames.GATEWAY_AUTH, "wrong");
        request.addHeader(HttpHeaderNames.USER_ID, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("CHAT-AUTH-001");
    }

    @Test
    void doFilter_allowsWebSocketHandshakeWhenHeadersValid() throws ServletException, IOException {
        ChatSecurityProperties properties = new ChatSecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("secret-token");

        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/chat");
        request.addHeader(HttpHeaderNames.GATEWAY_AUTH, "secret-token");
        request.addHeader(HttpHeaderNames.USER_ID, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_rejectsWhenSignedHeaderValidationFails() throws ServletException, IOException {
        ChatSecurityProperties properties = new ChatSecurityProperties();
        properties.setGatewayAuthEnabled(false);

        SignedHeaderParser parser = new SignedHeaderParser(new HmacSigner("gateway-sign-key"), 300_000);
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, parser);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/rooms");
        request.addHeader(HttpHeaderNames.USER_ID, "10");
        request.addHeader(HttpHeaderNames.USER_ROLES, "ROLE_USER");
        request.addHeader(HttpHeaderNames.NONCE, "nonce-1");
        request.addHeader(HttpHeaderNames.TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        request.addHeader(HttpHeaderNames.SIGNATURE, "invalid-signature");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("CHAT-AUTH-002");
    }

    @Test
    void doFilter_allowsWhenSignedHeadersValid() throws ServletException, IOException {
        ChatSecurityProperties properties = new ChatSecurityProperties();
        properties.setGatewayAuthEnabled(false);

        HmacSigner signer = new HmacSigner("gateway-sign-key");
        SignedHeaderParser parser = new SignedHeaderParser(signer, 300_000);
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, parser);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/rooms");
        String userId = "10";
        String roles = "ROLE_USER,ROLE_PLATFORM_ADMIN";
        String nonce = "nonce-2";
        long timestamp = System.currentTimeMillis();
        String payload = HmacSigner.buildSignaturePayload(userId, roles, nonce, timestamp);
        String signature = signer.sign(payload);

        request.addHeader(HttpHeaderNames.USER_ID, userId);
        request.addHeader(HttpHeaderNames.USER_ROLES, roles);
        request.addHeader(HttpHeaderNames.NONCE, nonce);
        request.addHeader(HttpHeaderNames.TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(HttpHeaderNames.SIGNATURE, signature);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
