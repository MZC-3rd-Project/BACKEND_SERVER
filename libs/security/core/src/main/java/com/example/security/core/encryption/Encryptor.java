package com.example.security.core.encryption;

public interface Encryptor {

    String encrypt(String plainText);

    String decrypt(String cipherText);

    String getAlgorithm();
}
