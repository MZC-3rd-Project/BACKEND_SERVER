package com.example.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionClaimParserTest {

    private final SessionClaimParser parser = new SessionClaimParser();

    @Test
    void parseClaims_extractsUserIdAndRoles() {
        GatewaySessionPrincipal principal = parser.parseClaims(Map.of(
                "userId", 101,
                "roles", List.of("buyer", "user"),
                "scope", "chat:write chat:read",
                "sid", "sid-101"
        ));

        assertThat(principal.userId()).isEqualTo(101L);
        assertThat(principal.roles()).containsExactly("BUYER", "USER", "CHAT:WRITE", "CHAT:READ");
        assertThat(principal.sessionId()).isEqualTo("sid-101");
    }

    @Test
    void parseClaims_supportsKeycloakStyleClaims() {
        GatewaySessionPrincipal principal = parser.parseClaims(Map.of(
                "sub", "202",
                "realm_access", Map.of("roles", List.of("admin", "staff"))
        ));

        assertThat(principal.userId()).isEqualTo(202L);
        assertThat(principal.roles()).containsExactly("ADMIN", "STAFF");
        assertThat(principal.sessionId()).isNull();
    }

    @Test
    void parseClaims_supportsSnowflakeIdClaimFromKeycloakMapper() {
        GatewaySessionPrincipal principal = parser.parseClaims(Map.of(
                "snowflakeId", "404",
                "realm_access", Map.of("roles", List.of("buyer"))
        ));

        assertThat(principal.userId()).isEqualTo(404L);
        assertThat(principal.roles()).containsExactly("BUYER");
    }

    @Test
    void parseClaims_supportsSessionStateFallbackClaim() {
        GatewaySessionPrincipal principal = parser.parseClaims(Map.of(
                "sub", "303",
                "session_state", "session-state-303"
        ));

        assertThat(principal.userId()).isEqualTo(303L);
        assertThat(principal.sessionId()).isEqualTo("session-state-303");
    }

    @Test
    void parseClaims_throwsWhenNoValidUserIdClaimExists() {
        assertThatThrownBy(() -> parser.parseClaims(Map.of("sub", "not-number")))
                .isInstanceOf(SessionClaimParseException.class)
                .hasMessageContaining("사용자 ID");
    }
}
