package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import com.example.gateway.security.session.domain.GatewaySessionView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class RedisGatewaySessionRepository implements GatewaySessionRepository {

    private static final String STATUS_UNKNOWN = "UNKNOWN";
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
    public Mono<String> findStatusBySid(String sessionId) {
        return redisTemplate.opsForHash()
                .get(sessionKey(sessionId), sessionProperties.getStatusField())
                .map(String::valueOf);
    }

    @Override
    public Mono<Void> activateSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return Mono.empty();
        }
        return redisTemplate.opsForHash()
                .put(sessionKey(sessionId), sessionProperties.getStatusField(), sessionProperties.getActiveStatus())
                .then(redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId))
                .then();
    }

    @Override
    public Mono<Void> indexUserSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return Mono.empty();
        }
        return redisTemplate.opsForSet()
                .add(userSessionsKey(userId), sessionId)
                .then();
    }

    @Override
    public Mono<Void> saveSession(GatewayServerSession session) {
        if (session == null || !StringUtils.hasText(session.sessionId()) || session.userId() == null || session.userId() <= 0) {
            return Mono.empty();
        }
        Map<String, String> sessionFields = new LinkedHashMap<>();
        sessionFields.put(sessionProperties.getStatusField(), defaultString(session.status(), sessionProperties.getActiveStatus()));
        sessionFields.put(USER_ID_FIELD, String.valueOf(session.userId()));
        sessionFields.put(ROLES_FIELD, String.join(",", session.roles() == null ? List.of() : session.roles()));
        sessionFields.put(KEYCLOAK_SUBJECT_FIELD, defaultString(session.keycloakSubject(), ""));
        sessionFields.put(KEYCLOAK_SESSION_ID_FIELD, defaultString(session.keycloakSessionId(), ""));
        sessionFields.put(EMAIL_FIELD, defaultString(session.email(), ""));
        sessionFields.put(REFRESH_TOKEN_ENCRYPTED_FIELD, defaultString(session.refreshTokenEncrypted(), ""));
        sessionFields.put(REFRESH_TOKEN_HASH_FIELD, defaultString(session.refreshTokenHash(), ""));
        sessionFields.put(TOKEN_FAMILY_ID_FIELD, defaultString(session.tokenFamilyId(), ""));
        sessionFields.put(ISSUED_AT_FIELD, String.valueOf(session.issuedAtEpochMillis()));
        sessionFields.put(ACCESS_TOKEN_EXPIRES_AT_FIELD, String.valueOf(session.accessTokenExpiresAtEpochMillis()));
        sessionFields.put(REFRESH_ROTATED_AT_FIELD, String.valueOf(session.refreshRotatedAtEpochMillis()));
        sessionFields.put(LAST_SEEN_AT_FIELD, String.valueOf(session.lastSeenAtEpochMillis()));
        return redisTemplate.opsForHash()
                .putAll(sessionKey(session.sessionId()), sessionFields)
                .then(indexUserSession(session.userId(), session.sessionId()));
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
                .map(entries -> mapToSession(sessionId, entries));
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
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(REFRESH_TOKEN_ENCRYPTED_FIELD, defaultString(refreshTokenEncrypted, ""));
        fields.put(REFRESH_TOKEN_HASH_FIELD, defaultString(refreshTokenHash, ""));
        fields.put(TOKEN_FAMILY_ID_FIELD, defaultString(tokenFamilyId, ""));
        fields.put(ACCESS_TOKEN_EXPIRES_AT_FIELD, String.valueOf(accessTokenExpiresAtEpochMillis));
        fields.put(REFRESH_ROTATED_AT_FIELD, String.valueOf(refreshRotatedAtEpochMillis));
        fields.put(LAST_SEEN_AT_FIELD, String.valueOf(lastSeenAtEpochMillis));
        return redisTemplate.opsForHash()
                .putAll(sessionKey(sessionId), fields)
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
        return markRevoked(sessionId);
    }

    @Override
    public Flux<GatewaySessionView> findSessionsByUserId(Long userId) {
        if (userId == null) {
            return Flux.empty();
        }
        return redisTemplate.opsForSet()
                .members(userSessionsKey(userId))
                .flatMap(sessionId -> findStatusBySid(sessionId)
                        .defaultIfEmpty(STATUS_UNKNOWN)
                        .map(status -> new GatewaySessionView(sessionId, status)));
    }

    @Override
    public Mono<Long> revokeAllByUserId(Long userId) {
        if (userId == null) {
            return Mono.just(0L);
        }
        return redisTemplate.opsForSet()
                .members(userSessionsKey(userId))
                .flatMap(sessionId -> markRevoked(sessionId).thenReturn(sessionId))
                .count();
    }

    private Mono<Void> markRevoked(String sessionId) {
        return redisTemplate.opsForHash()
                .put(sessionKey(sessionId), sessionProperties.getStatusField(), sessionProperties.getRevokedStatus())
                .then();
    }

    private GatewayServerSession mapToSession(String sessionId, Map<String, String> entries) {
        return new GatewayServerSession(
                sessionId,
                parseLong(entries.get(USER_ID_FIELD)),
                parseRoles(entries.get(ROLES_FIELD)),
                emptyToNull(entries.get(KEYCLOAK_SUBJECT_FIELD)),
                emptyToNull(entries.get(KEYCLOAK_SESSION_ID_FIELD)),
                emptyToNull(entries.get(EMAIL_FIELD)),
                emptyToNull(entries.get(REFRESH_TOKEN_ENCRYPTED_FIELD)),
                emptyToNull(entries.get(REFRESH_TOKEN_HASH_FIELD)),
                emptyToNull(entries.get(TOKEN_FAMILY_ID_FIELD)),
                entries.getOrDefault(sessionProperties.getStatusField(), STATUS_UNKNOWN),
                parseLong(entries.get(ISSUED_AT_FIELD), 0L),
                parseLong(entries.get(ACCESS_TOKEN_EXPIRES_AT_FIELD), 0L),
                parseLong(entries.get(REFRESH_ROTATED_AT_FIELD), 0L),
                parseLong(entries.get(LAST_SEEN_AT_FIELD), 0L)
        );
    }

    private String sessionKey(String sessionId) {
        return sessionProperties.getRedisKeyPrefix() + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return sessionProperties.getUserSessionsKeyPrefix() + userId;
    }

    private Long parseLong(String raw) {
        return parseLong(raw, null);
    }

    private Long parseLong(String raw, Long defaultValue) {
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
