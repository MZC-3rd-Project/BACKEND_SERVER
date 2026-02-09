package com.example.security.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec secretKeySpec;

    public HmacSigner(String signingKey) {
        this.secretKeySpec = new SecretKeySpec(
                signingKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new SecurityException("HMAC 서명 생성에 실패했습니다", e);
        }
    }

    public boolean verify(String payload, String signature) {
        String computed = sign(payload);
        return java.security.MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public static String buildSignaturePayload(String userId, String roles, String nonce, long timestamp) {
        if (userId.contains("|") || roles.contains("|") || nonce.contains("|")) {
            throw new IllegalArgumentException("서명 페이로드 필드에 구분자(|)를 포함할 수 없습니다");
        }
        return String.join("|", userId, roles, nonce, String.valueOf(timestamp));
    }
}
