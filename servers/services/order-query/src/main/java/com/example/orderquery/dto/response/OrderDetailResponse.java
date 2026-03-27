package com.example.orderquery.dto.response;

import com.example.orderquery.entity.OrderItem;
import com.example.orderquery.entity.Orders;
import com.example.orderquery.entity.Shipment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderDetailResponse {

    private Long orderId;
    private Long userId;
    private String status;
    private String sourceType;
    private Long totalAmount;
    private LocalDateTime createdAt;
    private ShipmentDto shipment;
    private List<OrderItemDto> items;

    @Getter
    @Builder
    public static class OrderItemDto {
        private Long orderItemId;
        private Long itemId;
        private Long storeId;
        private String storeNameSnap;
        private String titleSnap;
        private String itemTypeSnap;
        private Long unitPrice;
        private Long lineAmount;
        private Integer quantity;
        private String thumbnailUrl;
    }

    @Getter
    @Builder
    public static class ShipmentDto {
        private String recipientName;
        private String shippingAddress;
        private String deliveryType;
        private String status;
        private LocalDateTime shippedAt;
        private LocalDateTime deliveredAt;
    }

    public static OrderDetailResponse of(Orders order, List<OrderItem> items, Shipment shipment) {
        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .sourceType(order.getSourceType() != null ? order.getSourceType().name() : null)
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .shipment(shipment == null ? null : ShipmentDto.builder()
                        .recipientName(shipment.getRecipientName())
                        .shippingAddress(shipment.getShippingAddress())
                        .deliveryType(shipment.getDeliveryType() != null ? shipment.getDeliveryType().name() : null)
                        .status(shipment.getStatus() != null ? shipment.getStatus().name() : null)
                        .shippedAt(shipment.getShippedAt())
                        .deliveredAt(shipment.getDeliveredAt())
                        .build())
                .items(items.stream().map(item -> OrderItemDto.builder()
                        .orderItemId(item.getId())
                        .itemId(item.getItemId())
                        .storeId(item.getStoreId())
                        .storeNameSnap(item.getStoreNameSnap())
                        .titleSnap(item.getTitleSnap())
                        .itemTypeSnap(item.getItemTypeSnap() != null ? item.getItemTypeSnap().name() : null)
                        .unitPrice(item.getUnitPrice())
                        .lineAmount(item.getLineAmount())
                        .quantity(item.getQuantity())
                        .thumbnailUrl(item.getThumbnailUrl())
                        .build())
                        .toList())
                .build();
    }
}
