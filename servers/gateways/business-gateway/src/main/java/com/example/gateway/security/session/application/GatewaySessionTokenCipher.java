package com.example.gateway.security.session.application;

import com.example.gateway.config.BusinessGatewaySessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class GatewaySessionTokenCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final BusinessGatewaySessionProperties sessionProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new IllegalArgumentException("refresh token이 비어 있습니다");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey(), "AES"), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("refresh token 암호화에 실패했습니다", e);
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new IllegalArgumentException("암호화된 refresh token이 비어 있습니다");
        }
        try {
            byte[] combined = Base64.getUrlDecoder().decode(cipherText);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey(), "AES"), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("refresh token 복호화에 실패했습니다", e);
        }
    }

    private byte[] secretKey() {
        String rawSecret = StringUtils.hasText(sessionProperties.getRefreshTokenEncryptionSecret())
                ? sessionProperties.getRefreshTokenEncryptionSecret()
                : sessionProperties.getRefreshTokenHashPepper();
        if (!StringUtils.hasText(rawSecret)) {
            throw new IllegalStateException("refresh token 암호화 키가 비어 있습니다");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawSecret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("암호화 키 생성에 실패했습니다", e);
        }
    }
}
