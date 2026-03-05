package com.example.profile.crypto;

import com.example.security.crypto.encryption.AesGcmEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringAttributeConverterTest {

    private EncryptedStringAttributeConverter converter;

    @BeforeEach
    void setUp() {
        ProfileEncryptorHolder.setEncryptor(new AesGcmEncryptor("1234567890abcdef"));
        converter = new EncryptedStringAttributeConverter();
    }

    @Test
    void convert_roundTrip_encryptsAndDecrypts() {
        String plain = "alice@example.com";

        String encrypted = converter.convertToDatabaseColumn(plain);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(encrypted).startsWith(EncryptedStringAttributeConverter.ENCRYPTED_PREFIX);
        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void convertToEntityAttribute_legacyPlainText_isReturnedAsIs() {
        String legacy = "010-1234-5678";

        String decrypted = converter.convertToEntityAttribute(legacy);

        assertThat(decrypted).isEqualTo(legacy);
    }

    @Test
    void convertToDatabaseColumn_alreadyEncryptedValue_isNotEncryptedTwice() {
        String encrypted = converter.convertToDatabaseColumn("alice@example.com");

        String unchanged = converter.convertToDatabaseColumn(encrypted);

        assertThat(unchanged).isEqualTo(encrypted);
    }
}
