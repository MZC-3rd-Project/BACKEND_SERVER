package com.example.security.context;

import com.example.contracts.http.HttpHeaderNames;

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
        String userId = headerAccessor.apply(HttpHeaderNames.USER_ID);
        String rolesStr = headerAccessor.apply(HttpHeaderNames.USER_ROLES);
        String nonce = headerAccessor.apply(HttpHeaderNames.NONCE);
        String timestampStr = headerAccessor.apply(HttpHeaderNames.TIMESTAMP);
        String signature = headerAccessor.apply(HttpHeaderNames.SIGNATURE);

        if (userId == null || rolesStr == null || nonce == null || timestampStr == null || signature == null) {
            throw new HeaderSecurityException("필수 보안 헤더가 누락되었습니다");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new HeaderSecurityException("유효하지 않은 타임스탬프입니다");
        }

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
