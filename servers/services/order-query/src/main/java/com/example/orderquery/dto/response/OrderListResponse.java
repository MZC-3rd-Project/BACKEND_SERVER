package com.example.orderquery.dto.response;

import com.example.orderquery.entity.OrderItem;
import com.example.orderquery.entity.Orders;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderListResponse {

    private Long orderId;
    private String status;
    private String sourceType;
    private Long totalAmount;
    private LocalDateTime createdAt;
    private List<OrderItemSummary> items;

    @Getter
    @Builder
    public static class OrderItemSummary {
        private Long itemId;
        private String titleSnap;
        private String itemTypeSnap;
        private Integer quantity;
        private String thumbnailUrl;
    }

    public static OrderListResponse of(Orders order, List<OrderItem> items) {
        return OrderListResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .sourceType(order.getSourceType().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .items(items.stream().map(item -> OrderItemSummary.builder()
                        .itemId(item.getItemId())
                        .titleSnap(item.getTitleSnap())
                        .itemTypeSnap(item.getItemTypeSnap().name())
                        .quantity(item.getQuantity())
                        .thumbnailUrl(item.getThumbnailUrl())
                        .build())
                        .toList())
                .build();
    }
}
