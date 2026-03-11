package com.example.store.dto.response;

public record StoreDeleteResponse(
    Long id
) {
    public static StoreDeleteResponse of(Long id){
        return new StoreDeleteResponse(id);
    }
}
