package com.example.security.core;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class SignedHeaderParser {

    private final HmacSigner signer;
    private final long maxAgeMillis;

    public SignedHeaderParser(HmacSigner signer, long maxAgeMillis) {
        this.signer = signer;
        this.maxAgeMillis = maxAgeMillis;
    }

    public AuthContext parse(Function<String, String> headerAccessor) {
        String userId = headerAccessor.apply(SecurityConstants.HEADER_USER_ID);
        String rolesStr = headerAccessor.apply(SecurityConstants.HEADER_USER_ROLES);
        String nonce = headerAccessor.apply(SecurityConstants.HEADER_NONCE);
        String timestampStr = headerAccessor.apply(SecurityConstants.HEADER_TIMESTAMP);
        String signature = headerAccessor.apply(SecurityConstants.HEADER_SIGNATURE);

        if (userId == null || rolesStr == null || nonce == null || timestampStr == null || signature == null) {
            throw new HeaderSecurityException("필수 보안 헤더가 누락되었습니다");
        }

        long timestamp = Long.parseLong(timestampStr);

        long age = System.currentTimeMillis() - timestamp;
        if (age > maxAgeMillis || age < -maxAgeMillis) {
            throw new HeaderSecurityException("서명이 만료되었습니다");
        }

        String payload = HmacSigner.buildSignaturePayload(userId, rolesStr, nonce, timestamp);
        if (!signer.verify(payload, signature)) {
            throw new HeaderSecurityException("서명 검증에 실패했습니다");
        }

        List<String> roles = Arrays.asList(rolesStr.split(","));

        return AuthContext.builder()
                .userId(userId)
                .roles(roles)
                .nonce(nonce)
                .timestamp(timestamp)
                .build();
    }
}
