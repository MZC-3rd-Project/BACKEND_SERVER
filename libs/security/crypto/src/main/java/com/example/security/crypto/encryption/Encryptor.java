package com.example.security.crypto.encryption;

public interface Encryptor {

    String encrypt(String plainText);

    String decrypt(String cipherText);

    String getAlgorithm();
}
