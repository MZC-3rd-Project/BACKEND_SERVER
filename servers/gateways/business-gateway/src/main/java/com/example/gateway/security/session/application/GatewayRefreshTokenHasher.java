package com.example.gateway.security.session.application;

import com.example.gateway.config.BusinessGatewaySessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class GatewayRefreshTokenHasher {

    private final BusinessGatewaySessionProperties sessionProperties;

    public String hash(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("refresh token이 비어 있습니다");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(refreshToken.getBytes(StandardCharsets.UTF_8));
            if (StringUtils.hasText(sessionProperties.getRefreshTokenHashPepper())) {
                digest.update(sessionProperties.getRefreshTokenHashPepper().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("refresh token hash 생성에 실패했습니다", e);
        }
    }
}
