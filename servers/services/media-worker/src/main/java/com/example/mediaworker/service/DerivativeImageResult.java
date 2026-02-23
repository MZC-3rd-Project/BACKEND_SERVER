package com.example.mediaworker.service;

public record DerivativeImageResult(
        byte[] bytes,
        String contentType,
        String extension,
        int width,
        int height
) {
}
