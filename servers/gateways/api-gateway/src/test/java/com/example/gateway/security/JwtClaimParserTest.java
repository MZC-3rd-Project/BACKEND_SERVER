package com.example.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtClaimParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JwtClaimParser parser = new JwtClaimParser();

    @Test
    void parse_extractsUserIdAndRoles() {
        String jwt = buildJwt(Map.of(
                "userId", 101,
                "roles", List.of("buyer", "user"),
                "scope", "chat:write chat:read"
        ));

        GatewayJwtPrincipal principal = parser.parse(jwt);

        assertThat(principal.userId()).isEqualTo(101L);
        assertThat(principal.roles()).containsExactly("BUYER", "USER", "CHAT:WRITE", "CHAT:READ");
    }

    @Test
    void parse_supportsKeycloakStyleClaims() {
        String jwt = buildJwt(Map.of(
                "sub", "202",
                "realm_access", Map.of("roles", List.of("admin", "staff"))
        ));

        GatewayJwtPrincipal principal = parser.parse(jwt);

        assertThat(principal.userId()).isEqualTo(202L);
        assertThat(principal.roles()).containsExactly("ADMIN", "STAFF");
    }

    @Test
    void parse_throwsWhenNoValidUserIdClaimExists() {
        String jwt = buildJwt(Map.of("sub", "not-number"));

        assertThatThrownBy(() -> parser.parse(jwt))
                .isInstanceOf(JwtClaimParseException.class)
                .hasMessageContaining("사용자 ID");
    }

    private String buildJwt(Map<String, Object> claims) {
        try {
            String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
            String payload = base64Url(OBJECT_MAPPER.writeValueAsString(claims));
            return "%s.%s.".formatted(header, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String base64Url(String raw) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
