package com.example.store.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.store.entity.StoreStatus;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class StoreCreateResponse {
    @SnowflakeId
    private Long id; // store Id

    @SnowflakeId
    private Long userId;

    private String storeName;
    private StoreStatus status;

    public static StoreCreateResponse of(Long id,Long userId, String storeName, StoreStatus status){
        return StoreCreateResponse.builder()
            .id(id)
            .userId(userId)
            .storeName(storeName)
            .status(status)
            .build();
    }
}
