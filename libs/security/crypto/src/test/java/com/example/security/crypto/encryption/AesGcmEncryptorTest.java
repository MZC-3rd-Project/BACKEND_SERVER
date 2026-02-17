package com.example.security.crypto.encryption;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptorTest {

    @Test
    void shouldEncryptAndDecrypt() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor("1234567890abcdef");
        String plain = "hello-commerce";

        String encrypted = encryptor.encrypt(plain);
        String decrypted = encryptor.decrypt(encrypted);

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void shouldThrowForInvalidKeyLength() {
        assertThatThrownBy(() -> new AesGcmEncryptor("short-key"))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("키 길이");
    }
}
