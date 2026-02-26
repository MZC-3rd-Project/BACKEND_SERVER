package com.example.auth.dto.response;

public record CheckAvailableResponse(
        boolean available
) {
    public static CheckAvailableResponse of(boolean available) {
        return new CheckAvailableResponse(available);
    }
}
