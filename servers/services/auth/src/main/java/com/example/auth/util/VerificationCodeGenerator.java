package com.example.auth.util;

import java.security.SecureRandom;

public final class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;

    private VerificationCodeGenerator() {
    }

    public static String generate() {
        int code = RANDOM.nextInt(900_000) + 100_000;
        return String.valueOf(code);
    }
}
