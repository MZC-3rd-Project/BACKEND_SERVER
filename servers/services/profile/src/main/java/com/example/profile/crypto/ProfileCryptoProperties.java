package com.example.profile.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.crypto")
public class ProfileCryptoProperties {

    private String encryptionKey = ProfileEncryptorHolder.DEFAULT_KEY;

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
