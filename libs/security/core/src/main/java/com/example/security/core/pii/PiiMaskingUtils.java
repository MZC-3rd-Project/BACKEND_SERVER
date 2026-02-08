package com.example.security.core.pii;

import java.util.regex.Pattern;

public final class PiiMaskingUtils {

    private PiiMaskingUtils() {}

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\d{2,3})-?(\\d{3,4})-?(\\d{4})");
    private static final Pattern CARD_PATTERN =
            Pattern.compile("(\\d{4})-?(\\d{4})-?(\\d{4})-?(\\d{4})");

    public static String mask(String value, PiiType type) {
        if (value == null || value.isEmpty()) return value;

        return switch (type) {
            case EMAIL -> maskEmail(value);
            case PHONE -> maskPhone(value);
            case NAME -> maskName(value);
            case CARD_NUMBER -> maskCardNumber(value);
            case SSN -> maskSsn(value);
            case ADDRESS -> maskAddress(value);
            case GENERAL -> maskGeneral(value);
        };
    }

    public static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***@***";
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        int show = Math.min(2, local.length());
        return local.substring(0, show) + "***" + domain;
    }

    public static String maskPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return "***-****";
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    public static String maskName(String name) {
        if (name.length() <= 1) return "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    public static String maskCardNumber(String card) {
        String digits = card.replaceAll("[^0-9]", "");
        if (digits.length() < 12) return "****-****-****-****";
        return digits.substring(0, 4) + "-****-****-" + digits.substring(digits.length() - 4);
    }

    public static String maskSsn(String ssn) {
        String digits = ssn.replaceAll("[^0-9]", "");
        if (digits.length() < 6) return "******-*******";
        return digits.substring(0, 6) + "-*******";
    }

    public static String maskAddress(String address) {
        if (address.length() <= 10) return "***";
        return address.substring(0, 10) + "***";
    }

    public static String maskGeneral(String value) {
        if (value.length() <= 2) return "**";
        int show = Math.min(2, value.length() / 3);
        return value.substring(0, show) + "*".repeat(value.length() - show);
    }

    /**
     * 로그 메시지에서 PII 패턴을 자동 탐지하여 마스킹한다.
     * 이메일, 전화번호, 카드번호 패턴을 자동 감지한다.
     */
    public static String maskLogMessage(String message) {
        if (message == null) return null;

        String result = EMAIL_PATTERN.matcher(message).replaceAll(match -> maskEmail(match.group()));
        result = CARD_PATTERN.matcher(result).replaceAll(match -> maskCardNumber(match.group()));
        result = PHONE_PATTERN.matcher(result).replaceAll(match -> maskPhone(match.group()));

        return result;
    }
}
