package com.example.clients.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreateLineItem(
        String channelType,
        Long channelRefId,
        Long itemId,
        String itemType,
        String title,
        Long sellerId,
        Long storeId,
        String stockItemType,
        Long referenceId,
        String referenceName,
        Integer quantity,
        Long baseUnitPrice,
        Long finalUnitPrice,
        Long lineAmount
) {
}
