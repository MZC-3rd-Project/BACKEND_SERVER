package com.example.store.dto.request;

import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.ImageType;
import com.example.store.entity.StoreStatus;

import java.util.List;


public record StoreUpdateRequest(
    String storeName,
    StoreStatus status,

    String address,
    AddressType addressType,

    String description,

    String contactValue,
    ContactType contactType,

    List<StoreImageRequest> images
) {


    public record StoreImageRequest(
        ImageType imageType,       // THUMBNAIL | BANNER | INTRODUCE
        Long mediaId,
        int sortOrder
    ) {

    }
}
