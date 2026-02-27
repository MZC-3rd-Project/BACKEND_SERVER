package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.port.GatewayRefreshTokenFamilyRepository;
import com.example.gateway.security.session.domain.GatewayRefreshTokenFamilyState;
import com.example.gateway.security.session.domain.RefreshTokenFamilyRotateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class RedisGatewayRefreshTokenFamilyRepository implements GatewayRefreshTokenFamilyRepository {

    private static final RedisScript<Long> ROTATE_CAS_SCRIPT = RedisScript.of(
            """
                    local key = KEYS[1]
                    local expectedHash = ARGV[1]
                    local nextHash = ARGV[2]
                    local uid = ARGV[3]
                    local sid = ARGV[4]
                    local rotatedAt = ARGV[5]
                    local currentHashField = ARGV[6]
                    local uidField = ARGV[7]
                    local sidField = ARGV[8]
                    local rotatedAtField = ARGV[9]
                    local reuseDetectedField = ARGV[10]
                    local current = redis.call('HGET', key, currentHashField)
                    if not current then
                      return -1
                    end
                    if current ~= expectedHash then
                      return 0
                    end
                    redis.call('HSET', key,
                      currentHashField, nextHash,
                      uidField, uid,
                      sidField, sid,
                      rotatedAtField, rotatedAt,
                      reuseDetectedField, 'false')
                    return 1
                    """,
            Long.class
    );

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySessionProperties properties;

    @Override
    public Mono<GatewayRefreshTokenFamilyState> findByFamilyId(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return Mono.empty();
        }
        String key = familyKey(familyId);
        return Mono.zip(
                        redisTemplate.opsForHash().get(key, properties.getRefreshUidField()).defaultIfEmpty(""),
                        redisTemplate.opsForHash().get(key, properties.getRefreshSidField()).defaultIfEmpty(""),
                        redisTemplate.opsForHash().get(key, properties.getRefreshCurrentHashField()).defaultIfEmpty(""),
                        redisTemplate.opsForHash().get(key, properties.getRefreshReuseDetectedField()).defaultIfEmpty("false")
                )
                .flatMap(tuple -> {
                    String userIdRaw = stringValue(tuple.getT1());
                    String sessionId = stringValue(tuple.getT2());
                    String currentHash = stringValue(tuple.getT3());
                    String reuseDetectedRaw = stringValue(tuple.getT4());
                    if (!StringUtils.hasText(currentHash)) {
                        return Mono.empty();
                    }
                    return Mono.just(new GatewayRefreshTokenFamilyState(
                            familyId,
                            parseLongOrNull(userIdRaw),
                            sessionId,
                            currentHash,
                            parseBoolean(reuseDetectedRaw)
                    ));
                });
    }

    @Override
    public Mono<Void> upsert(String familyId,
                             Long userId,
                             String sessionId,
                             String currentRefreshTokenHash,
                             boolean reuseDetected,
                             long rotatedAtEpochMillis) {
        if (!StringUtils.hasText(familyId) || !StringUtils.hasText(currentRefreshTokenHash)) {
            return Mono.empty();
        }
        String key = familyKey(familyId);
        return redisTemplate.opsForHash()
                .putAll(key, Map.of(
                        properties.getRefreshUidField(), userId == null ? "" : String.valueOf(userId),
                        properties.getRefreshSidField(), sessionId == null ? "" : sessionId,
                        properties.getRefreshCurrentHashField(), currentRefreshTokenHash,
                        properties.getRefreshRotatedAtField(), String.valueOf(rotatedAtEpochMillis),
                        properties.getRefreshReuseDetectedField(), String.valueOf(reuseDetected)
                ))
                .then();
    }

    @Override
    public Mono<RefreshTokenFamilyRotateResult> rotateIfCurrentHashMatches(String familyId,
                                                                           String expectedCurrentHash,
                                                                           String nextRefreshTokenHash,
                                                                           Long userId,
                                                                           String sessionId,
                                                                           long rotatedAtEpochMillis) {
        if (!StringUtils.hasText(familyId)
                || !StringUtils.hasText(expectedCurrentHash)
                || !StringUtils.hasText(nextRefreshTokenHash)) {
            return Mono.just(RefreshTokenFamilyRotateResult.FAMILY_NOT_FOUND);
        }

        String key = familyKey(familyId);
        String uidValue = userId == null ? "" : String.valueOf(userId);
        String sidValue = sessionId == null ? "" : sessionId;
        return redisTemplate.execute(
                        ROTATE_CAS_SCRIPT,
                        List.of(key),
                        expectedCurrentHash,
                        nextRefreshTokenHash,
                        uidValue,
                        sidValue,
                        String.valueOf(rotatedAtEpochMillis),
                        properties.getRefreshCurrentHashField(),
                        properties.getRefreshUidField(),
                        properties.getRefreshSidField(),
                        properties.getRefreshRotatedAtField(),
                        properties.getRefreshReuseDetectedField()
                )
                .next()
                .map(this::toRotateResult)
                .defaultIfEmpty(RefreshTokenFamilyRotateResult.FAMILY_NOT_FOUND);
    }

    @Override
    public Mono<Void> markReuseDetected(String familyId, long detectedAtEpochMillis) {
        if (!StringUtils.hasText(familyId)) {
            return Mono.empty();
        }
        String key = familyKey(familyId);
        return redisTemplate.opsForHash()
                .putAll(key, Map.of(
                        properties.getRefreshReuseDetectedField(), "true",
                        properties.getRefreshReuseDetectedAtField(), String.valueOf(detectedAtEpochMillis)
                ))
                .then();
    }

    private RefreshTokenFamilyRotateResult toRotateResult(Long scriptResult) {
        if (scriptResult == null) {
            return RefreshTokenFamilyRotateResult.FAMILY_NOT_FOUND;
        }
        if (scriptResult == 1L) {
            return RefreshTokenFamilyRotateResult.ROTATED;
        }
        if (scriptResult == 0L) {
            return RefreshTokenFamilyRotateResult.CURRENT_HASH_MISMATCH;
        }
        return RefreshTokenFamilyRotateResult.FAMILY_NOT_FOUND;
    }

    private String familyKey(String familyId) {
        return properties.getRefreshFamilyKeyPrefix() + familyId;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long parseLongOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean parseBoolean(String raw) {
        return StringUtils.hasText(raw) && Boolean.parseBoolean(raw);
    }
}
