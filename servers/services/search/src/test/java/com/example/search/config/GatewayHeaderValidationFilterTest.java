package com.example.search.config;

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
        SearchSecurityProperties properties = new SearchSecurityProperties();
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
        SearchSecurityProperties properties = new SearchSecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("secret-token");

        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/search/indexes/recreate");
        request.addHeader(HttpHeaderNames.GATEWAY_AUTH, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SEARCH-AUTH-001");
    }

    @Test
    void doFilter_allowsWhenGatewayTokenValid() throws ServletException, IOException {
        SearchSecurityProperties properties = new SearchSecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("secret-token");

        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/search/indexes/recreate");
        request.addHeader(HttpHeaderNames.GATEWAY_AUTH, "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_allowsWhenSignedParserPresentAndTokenNotConfigured() throws ServletException, IOException {
        SearchSecurityProperties properties = new SearchSecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("");

        HmacSigner signer = new HmacSigner("gateway-sign-key");
        SignedHeaderParser parser = new SignedHeaderParser(signer, 300_000);
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, parser);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/search/indexes/recreate");
        addSignedHeaders(request, signer, "user-1", "ROLE_INTERNAL");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_rejectsWhenSignedRoleIsNotInternal() throws ServletException, IOException {
        SearchSecurityProperties properties = new SearchSecurityProperties();
        properties.setGatewayAuthEnabled(true);
        properties.setInternalAuthToken("");

        HmacSigner signer = new HmacSigner("gateway-sign-key");
        SignedHeaderParser parser = new SignedHeaderParser(signer, 300_000);
        GatewayHeaderValidationFilter filter = new GatewayHeaderValidationFilter(properties, parser);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/search/indexes/recreate");
        addSignedHeaders(request, signer, "user-1", "ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private void addSignedHeaders(MockHttpServletRequest request, HmacSigner signer, String userId, String roles) {
        String nonce = "nonce-1";
        long timestamp = System.currentTimeMillis();
        String payload = HmacSigner.buildSignaturePayload(userId, roles, nonce, timestamp);
        String signature = signer.sign(payload);

        request.addHeader(HttpHeaderNames.USER_ID, userId);
        request.addHeader(HttpHeaderNames.USER_ROLES, roles);
        request.addHeader(HttpHeaderNames.NONCE, nonce);
        request.addHeader(HttpHeaderNames.TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(HttpHeaderNames.SIGNATURE, signature);
    }
}
