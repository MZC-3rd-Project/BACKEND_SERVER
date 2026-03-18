package com.example.chat.client;

public record ChatProductSnapshot(
    Long itemId,
    Long sellerId,
    Long storeId,
    String title,
    String status,
    Long thumbnailMediaId
) {
}
