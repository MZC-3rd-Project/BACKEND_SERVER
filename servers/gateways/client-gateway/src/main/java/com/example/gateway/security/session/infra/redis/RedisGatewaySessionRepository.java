package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisGatewaySessionRepository implements GatewaySessionRepository {

    private static final String STATUS_FIELD = "status";
    private static final String USER_ID_FIELD = "uid";
    private static final String ROLES_FIELD = "roles";
    private static final String KEYCLOAK_SUBJECT_FIELD = "kcSub";
    private static final String KEYCLOAK_SESSION_ID_FIELD = "kcSid";
    private static final String EMAIL_FIELD = "email";
    private static final String REFRESH_TOKEN_ENCRYPTED_FIELD = "refreshTokenEnc";
    private static final String REFRESH_TOKEN_HASH_FIELD = "refreshTokenHash";
    private static final String TOKEN_FAMILY_ID_FIELD = "tokenFamilyId";
    private static final String ISSUED_AT_FIELD = "issuedAt";
    private static final String ACCESS_TOKEN_EXPIRES_AT_FIELD = "accessTokenExpiresAt";
    private static final String REFRESH_ROTATED_AT_FIELD = "refreshRotatedAt";
    private static final String LAST_SEEN_AT_FIELD = "lastSeenAt";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySessionProperties sessionProperties;

    @Override
    public Mono<Void> saveSession(GatewayServerSession session) {
        if (session == null || !StringUtils.hasText(session.sessionId()) || session.userId() == null || session.userId() <= 0) {
            return Mono.empty();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(STATUS_FIELD, defaultString(session.status(), sessionProperties.getActiveStatus()));
        fields.put(USER_ID_FIELD, String.valueOf(session.userId()));
        fields.put(ROLES_FIELD, String.join(",", session.roles() == null ? List.of() : session.roles()));
        fields.put(KEYCLOAK_SUBJECT_FIELD, defaultString(session.keycloakSubject(), ""));
        fields.put(KEYCLOAK_SESSION_ID_FIELD, defaultString(session.keycloakSessionId(), ""));
        fields.put(EMAIL_FIELD, defaultString(session.email(), ""));
        fields.put(REFRESH_TOKEN_ENCRYPTED_FIELD, defaultString(session.refreshTokenEncrypted(), ""));
        fields.put(REFRESH_TOKEN_HASH_FIELD, defaultString(session.refreshTokenHash(), ""));
        fields.put(TOKEN_FAMILY_ID_FIELD, defaultString(session.tokenFamilyId(), ""));
        fields.put(ISSUED_AT_FIELD, String.valueOf(session.issuedAtEpochMillis()));
        fields.put(ACCESS_TOKEN_EXPIRES_AT_FIELD, String.valueOf(session.accessTokenExpiresAtEpochMillis()));
        fields.put(REFRESH_ROTATED_AT_FIELD, String.valueOf(session.refreshRotatedAtEpochMillis()));
        fields.put(LAST_SEEN_AT_FIELD, String.valueOf(session.lastSeenAtEpochMillis()));
        return redisTemplate.opsForHash().putAll(sessionKey(session.sessionId()), fields)
                .then(redisTemplate.opsForSet().add(userSessionsKey(session.userId()), session.sessionId()))
                .then();
    }

    @Override
    public Mono<GatewayServerSession> findSessionById(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        return redisTemplate.opsForHash()
                .entries(sessionKey(sessionId))
                .collectMap(entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue()))
                .filter(entries -> !entries.isEmpty())
                .map(entries -> new GatewayServerSession(
                        sessionId,
                        parseLong(entries.get(USER_ID_FIELD)),
                        parseRoles(entries.get(ROLES_FIELD)),
                        emptyToNull(entries.get(KEYCLOAK_SUBJECT_FIELD)),
                        emptyToNull(entries.get(KEYCLOAK_SESSION_ID_FIELD)),
                        emptyToNull(entries.get(EMAIL_FIELD)),
                        emptyToNull(entries.get(REFRESH_TOKEN_ENCRYPTED_FIELD)),
                        emptyToNull(entries.get(REFRESH_TOKEN_HASH_FIELD)),
                        emptyToNull(entries.get(TOKEN_FAMILY_ID_FIELD)),
                        entries.getOrDefault(STATUS_FIELD, sessionProperties.getRevokedStatus()),
                        parseLong(entries.get(ISSUED_AT_FIELD), 0L),
                        parseLong(entries.get(ACCESS_TOKEN_EXPIRES_AT_FIELD), 0L),
                        parseLong(entries.get(REFRESH_ROTATED_AT_FIELD), 0L),
                        parseLong(entries.get(LAST_SEEN_AT_FIELD), 0L)
                ));
    }

    @Override
    public Mono<Void> updateSessionTokens(String sessionId,
                                          String refreshTokenEncrypted,
                                          String refreshTokenHash,
                                          String tokenFamilyId,
                                          long accessTokenExpiresAtEpochMillis,
                                          long refreshRotatedAtEpochMillis,
                                          long lastSeenAtEpochMillis) {
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        return redisTemplate.opsForHash()
                .putAll(sessionKey(sessionId), Map.of(
                        REFRESH_TOKEN_ENCRYPTED_FIELD, defaultString(refreshTokenEncrypted, ""),
                        REFRESH_TOKEN_HASH_FIELD, defaultString(refreshTokenHash, ""),
                        TOKEN_FAMILY_ID_FIELD, defaultString(tokenFamilyId, ""),
                        ACCESS_TOKEN_EXPIRES_AT_FIELD, String.valueOf(accessTokenExpiresAtEpochMillis),
                        REFRESH_ROTATED_AT_FIELD, String.valueOf(refreshRotatedAtEpochMillis),
                        LAST_SEEN_AT_FIELD, String.valueOf(lastSeenAtEpochMillis)
                ))
                .then();
    }

    @Override
    public Mono<Void> touchSession(String sessionId, long lastSeenAtEpochMillis) {
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        return redisTemplate.opsForHash()
                .put(sessionKey(sessionId), LAST_SEEN_AT_FIELD, String.valueOf(lastSeenAtEpochMillis))
                .then();
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        return redisTemplate.opsForHash()
                .put(sessionKey(sessionId), STATUS_FIELD, sessionProperties.getRevokedStatus())
                .then();
    }

    private String sessionKey(String sessionId) {
        return sessionProperties.getRedisKeyPrefix() + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return sessionProperties.getUserSessionsKeyPrefix() + userId;
    }

    private Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private long parseLong(String raw, long defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private List<String> parseRoles(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String emptyToNull(String raw) {
        return StringUtils.hasText(raw) ? raw : null;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
