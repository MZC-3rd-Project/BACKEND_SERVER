package com.example.product.dto.item.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.entity.item.ItemStatusHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StatusHistoryResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long itemId;

    private String previousStatus;
    private String newStatus;
    private String reason;

    @SnowflakeId
    private Long changedBy;

    private LocalDateTime createdAt;

    public static StatusHistoryResponse from(ItemStatusHistory entity) {
        return StatusHistoryResponse.builder()
                .id(entity.getId())
                .itemId(entity.getItemId())
                .previousStatus(entity.getPreviousStatus().name())
                .newStatus(entity.getNewStatus().name())
                .reason(entity.getReason())
                .changedBy(entity.getChangedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
