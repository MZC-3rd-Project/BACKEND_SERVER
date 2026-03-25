package com.example.gateway.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SessionClaimParser {

    private static final List<String> USER_ID_CLAIM_CANDIDATES =
            List.of("userId", "user_id", "uid", "memberId", "snowflakeId", "snowflake_id", "sub");
    private static final List<String> SESSION_ID_CLAIM_CANDIDATES =
            List.of("sid", "session_state");

    public GatewaySessionPrincipal parseClaims(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            throw new SessionClaimParseException("세션 클레임이 비어 있습니다");
        }

        Long userId = extractUserId(claims);
        if (userId == null || userId <= 0) {
            throw new SessionClaimParseException("세션 클레임에서 유효한 사용자 ID를 찾지 못했습니다");
        }

        return new GatewaySessionPrincipal(userId, extractRoles(claims), extractSessionId(claims));
    }

    private Long extractUserId(Map<String, Object> claims) {
        for (String claimName : USER_ID_CLAIM_CANDIDATES) {
            Long parsed = toPositiveLong(claims.get(claimName));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Long toPositiveLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String raw) {
            try {
                long parsed = Long.parseLong(raw.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String extractSessionId(Map<String, Object> claims) {
        for (String claimName : SESSION_ID_CLAIM_CANDIDATES) {
            Object value = claims.get(claimName);
            if (value == null) {
                continue;
            }
            String sessionId = String.valueOf(value).trim();
            if (StringUtils.hasText(sessionId)) {
                return sessionId;
            }
        }
        return null;
    }

    private List<String> extractRoles(Map<String, Object> claims) {
        Set<String> roles = new LinkedHashSet<>();
        addClaimValues(roles, claims.get("roles"));
        addClaimValues(roles, claims.get("role"));
        addClaimValues(roles, claims.get("authorities"));

        Object scope = claims.get("scope");
        if (scope instanceof String scopeText) {
            addDelimitedValues(roles, scopeText, "\\s+");
        }
        Object scp = claims.get("scp");
        if (scp instanceof String scopeText) {
            addDelimitedValues(roles, scopeText, "\\s+");
        } else {
            addClaimValues(roles, scp);
        }

        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            addClaimValues(roles, realmAccessMap.get("roles"));
        }

        return List.copyOf(roles);
    }

    private void addClaimValues(Set<String> roles, Object claimValue) {
        if (claimValue == null) {
            return;
        }
        if (claimValue instanceof Collection<?> collection) {
            for (Object value : collection) {
                addSingleRole(roles, value);
            }
            return;
        }
        if (claimValue instanceof String text) {
            if (text.contains(",")) {
                addDelimitedValues(roles, text, ",");
                return;
            }
            addSingleRole(roles, text);
            return;
        }
        addSingleRole(roles, claimValue);
    }

    private void addDelimitedValues(Set<String> roles, String text, String regex) {
        for (String token : text.split(regex)) {
            addSingleRole(roles, token);
        }
    }

    private void addSingleRole(Set<String> roles, Object value) {
        if (value == null) {
            return;
        }
        String normalized = String.valueOf(value).trim();
        if (!normalized.isEmpty()) {
            roles.add(normalized.toUpperCase(Locale.ROOT));
        }
    }
}
