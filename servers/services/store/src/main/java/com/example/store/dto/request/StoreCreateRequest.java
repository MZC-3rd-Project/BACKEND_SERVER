package com.example.store.dto.request;

import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.ImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StoreCreateRequest(
    @NotBlank(message = "store 이름을 입력해주세요")
    String storeName,

    @NotBlank(message = "주소는 필수 입니다!")
    String address,

    @NotNull(message = "주소 타입은 필수입니다!")
    AddressType addressType,

    @NotBlank(message = "연락처는 필수입니다!")
    String contactValue,

    @NotNull(message = "연락처 타입은 필수입니다!")
    ContactType contactType,

    String description,

    List<StoreImageRequest> images
) {

    public record StoreImageRequest(
        ImageType imageType,       // THUMBNAIL | GALLERY
        Long mediaId,
        int sortOrder
    ) { }

}
