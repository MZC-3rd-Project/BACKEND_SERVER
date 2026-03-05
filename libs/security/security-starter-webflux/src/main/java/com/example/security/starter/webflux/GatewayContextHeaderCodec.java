package com.example.security.starter.webflux;

import com.example.security.signature.HeaderSecurityException;
import com.example.security.signature.HmacSigner;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class GatewayContextHeaderCodec {

    private static final String PART_SEPARATOR_REGEX = "\\.";
    private static final int PART_COUNT = 5;

    private GatewayContextHeaderCodec() {
    }

    public static String encode(String userId, String roles, String nonce, long timestamp, String signature) {
        return encodePart(userId)
                + "."
                + encodePart(roles)
                + "."
                + encodePart(nonce)
                + "."
                + timestamp
                + "."
                + signature;
    }

    public static String encodeSigned(String userId, String roles, String nonce, long timestamp, HmacSigner signer) {
        String payload = HmacSigner.buildSignaturePayload(userId, roles, nonce, timestamp);
        String signature = signer.sign(payload);
        return encode(userId, roles, nonce, timestamp, signature);
    }

    public static ParsedGatewayContext decode(String token) {
        if (!StringUtils.hasText(token)) {
            throw new HeaderSecurityException("gateway context header가 비어 있습니다");
        }

        String[] parts = token.split(PART_SEPARATOR_REGEX, -1);
        if (parts.length != PART_COUNT) {
            throw new HeaderSecurityException("유효하지 않은 gateway context 형식입니다");
        }

        String userId = decodePart(parts[0], "userId");
        String roles = decodePart(parts[1], "roles");
        String nonce = decodePart(parts[2], "nonce");
        long timestamp;
        try {
            timestamp = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            throw new HeaderSecurityException("유효하지 않은 gateway context timestamp입니다");
        }

        String signature = parts[4];
        if (!StringUtils.hasText(signature)) {
            throw new HeaderSecurityException("유효하지 않은 gateway context signature입니다");
        }

        return new ParsedGatewayContext(userId, roles, nonce, timestamp, signature);
    }

    private static String encodePart(String raw) {
        String safe = raw == null ? "" : raw;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String encoded, String fieldName) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new HeaderSecurityException("유효하지 않은 gateway context " + fieldName + "입니다");
        }
    }

    public record ParsedGatewayContext(
            String userId,
            String roles,
            String nonce,
            long timestamp,
            String signature
    ) {
    }
}
