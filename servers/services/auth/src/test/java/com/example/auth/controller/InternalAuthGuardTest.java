package com.example.auth.controller;

import com.example.security.gateway.GatewaySecurityModuleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAuthGuardTest {

    @Test
    void isAuthorized_returnsTrueWhenHeaderMatchesConfiguredToken() {
        GatewaySecurityModuleProperties properties = new GatewaySecurityModuleProperties();
        properties.setInternalAuthHeader("X-Gateway-Auth");
        properties.setInternalAuthToken("secret-token");

        InternalAuthGuard guard = new InternalAuthGuard(properties);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Gateway-Auth", "secret-token");

        assertThat(guard.isAuthorized(headers)).isTrue();
    }

    @Test
    void isAuthorized_returnsFalseWhenHeaderMissingOrMismatched() {
        GatewaySecurityModuleProperties properties = new GatewaySecurityModuleProperties();
        properties.setInternalAuthHeader("X-Gateway-Auth");
        properties.setInternalAuthToken("secret-token");

        InternalAuthGuard guard = new InternalAuthGuard(properties);

        assertThat(guard.isAuthorized(new HttpHeaders())).isFalse();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Gateway-Auth", "wrong-token");
        assertThat(guard.isAuthorized(headers)).isFalse();
    }
}
