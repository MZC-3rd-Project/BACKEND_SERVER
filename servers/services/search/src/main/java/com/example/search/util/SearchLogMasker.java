package com.example.search.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SearchLogMasker {

    private SearchLogMasker() {
    }

    public static String keywordHash(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return "hash-error";
        }
    }
}
