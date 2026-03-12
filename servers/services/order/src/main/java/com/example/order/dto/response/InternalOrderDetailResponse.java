package com.example.order.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InternalOrderDetailResponse {

    @SnowflakeId
    private Long orderId;
    private Long userId;
    private String status;
    private Long totalAmount;
    private String recipientName;
    private String recipientPhone;
    private Long deliveryAddressId;
    private String deliveryMemo;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private List<OrderItemDto> items;

    @Getter
    @Builder
    public static class OrderItemDto {
        @SnowflakeId
        private Long id;
        private String channelType;
        private Long channelRefId;
        private Long itemId;
        private Long storeId;
        private Integer quantity;
        private Long unitPrice;
        private Long lineAmount;
    }

    public static InternalOrderDetailResponse from(Order order) {
        return InternalOrderDetailResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .deliveryAddressId(order.getDeliveryAddressId())
                .deliveryMemo(order.getDeliveryMemo())
                .expiresAt(order.getExpiresAt())
                .createdAt(order.getCreatedAt())
                .items(order.getOrderItems().stream()
                        .map(InternalOrderDetailResponse::toItemDto)
                        .toList())
                .build();
    }

    private static OrderItemDto toItemDto(OrderItem item) {
        return OrderItemDto.builder()
                .id(item.getId())
                .channelType(item.getChannelType())
                .channelRefId(item.getChannelRefId())
                .itemId(item.getItemId())
                .storeId(item.getStoreId())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineAmount(item.getLineAmount())
                .build();
    }
}
