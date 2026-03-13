package com.example.cart.dto.response;

import com.example.cart.domain.CartLine;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemResponse {

    private Long itemId;
    private Long referenceId;
    private String channelType;
    private Long channelRefId;
    private String stockItemType;
    private Integer quantity;
    private boolean selected;
    private Long storeId;
    private String itemTitle;
    private String thumbnailUrl;
    private String storeName;
    private Long displayPrice;
    private String salesStatus;
    private Instant updatedAt;
    private Long expiresAtEpoch;

    public static CartItemResponse from(CartLine line) {
        return CartItemResponse.builder()
                .itemId(line.getIdentity().itemId())
                .referenceId(line.getIdentity().referenceId())
                .channelType(line.getIdentity().channelType())
                .channelRefId(line.getIdentity().channelRefId())
                .stockItemType(line.getStockItemType())
                .quantity(line.getQuantity())
                .selected(line.isSelected())
                .storeId(line.getStoreId())
                .itemTitle(line.getItemTitle())
                .thumbnailUrl(line.getThumbnailUrl())
                .storeName(line.getStoreName())
                .displayPrice(line.getDisplayPrice())
                .salesStatus(line.getSalesStatus())
                .updatedAt(line.getUpdatedAt())
                .expiresAtEpoch(line.getExpiresAtEpoch())
                .build();
    }
}
