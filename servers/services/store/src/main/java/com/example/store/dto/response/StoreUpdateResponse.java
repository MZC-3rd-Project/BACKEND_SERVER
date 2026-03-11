package com.example.store.dto.response;


import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.ImageType;
import com.example.store.entity.StoreStatus;

import java.util.List;

public record StoreUpdateResponse(
    Long id,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String address,
    AddressType addressType,
    String contactValue,
    ContactType contactType,
    List<StoreImageResponse> images
) {
    public record StoreImageResponse(
        ImageType imageType,
        int sortOrder,
        Long mediaId) {}

    public static StoreUpdateResponse of(StoreUpdateResponse response) {
        return new StoreUpdateResponse(
            response.id(),
            response.userId(),
            response.storeName(),
            response.status(),
            response.description(),
            response.address(),
            response.addressType(),
            response.contactValue(),
            response.contactType(),
            response.images()
        );
    }
}
