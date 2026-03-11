package com.example.profile.crypto;

import com.example.security.crypto.encryption.EncryptionException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.util.StringUtils;

@Converter
public class EncryptedStringAttributeConverter implements AttributeConverter<String, String> {

    static final String ENCRYPTED_PREFIX = "enc::";

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (!StringUtils.hasText(attribute)) {
            return attribute;
        }
        if (isEncrypted(attribute)) {
            return attribute;
        }
        return ENCRYPTED_PREFIX + ProfileEncryptorHolder.getEncryptor().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return dbData;
        }
        if (!isEncrypted(dbData)) {
            // Backward compatibility for legacy plaintext rows.
            return dbData;
        }

        String cipherText = dbData.substring(ENCRYPTED_PREFIX.length());
        try {
            return ProfileEncryptorHolder.getEncryptor().decrypt(cipherText);
        } catch (EncryptionException exception) {
            throw new IllegalStateException("Failed to decrypt profile sensitive field", exception);
        }
    }

    static boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.startsWith(ENCRYPTED_PREFIX);
    }
}
