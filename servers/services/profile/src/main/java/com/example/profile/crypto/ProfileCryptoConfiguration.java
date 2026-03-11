package com.example.profile.crypto;

import com.example.security.crypto.encryption.AesGcmEncryptor;
import com.example.security.crypto.encryption.Encryptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProfileCryptoProperties.class)
public class ProfileCryptoConfiguration {

    @Bean
    public Encryptor profileEncryptor(ProfileCryptoProperties properties) {
        Encryptor encryptor = new AesGcmEncryptor(properties.getEncryptionKey());
        ProfileEncryptorHolder.setEncryptor(encryptor);
        return encryptor;
    }
}
