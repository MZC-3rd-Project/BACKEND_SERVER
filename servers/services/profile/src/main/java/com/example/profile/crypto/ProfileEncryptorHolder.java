package com.example.profile.crypto;

import com.example.security.crypto.encryption.AesGcmEncryptor;
import com.example.security.crypto.encryption.Encryptor;

import java.util.Objects;

public final class ProfileEncryptorHolder {

    static final String KEY_PROPERTY = "app.security.crypto.encryption-key";
    static final String KEY_ENV = "APP_SECURITY_CRYPTO_ENCRYPTION_KEY";
    static final String DEFAULT_KEY = "1234567890abcdef";

    private static volatile Encryptor encryptor;

    private ProfileEncryptorHolder() {
    }

    public static Encryptor getEncryptor() {
        Encryptor current = encryptor;
        if (current != null) {
            return current;
        }

        synchronized (ProfileEncryptorHolder.class) {
            if (encryptor == null) {
                encryptor = new AesGcmEncryptor(resolveKey());
            }
            return encryptor;
        }
    }

    public static void setEncryptor(Encryptor customEncryptor) {
        encryptor = Objects.requireNonNull(customEncryptor, "customEncryptor must not be null");
    }

    private static String resolveKey() {
        String fromProperty = trimToNull(System.getProperty(KEY_PROPERTY));
        if (fromProperty != null) {
            return fromProperty;
        }

        String fromEnv = trimToNull(System.getenv(KEY_ENV));
        if (fromEnv != null) {
            return fromEnv;
        }

        return DEFAULT_KEY;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
