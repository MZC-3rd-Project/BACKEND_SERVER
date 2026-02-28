package com.example.profile.dto.response;

import com.example.profile.entity.ProfileAddress;

public record ProfileAddressResponse(
    Long profileId,
    String deliveryName,
    String zipcode,
    String sido,
    String sigungu,
    String roadName,
    String buildingNumber,
    String buildingName,
    String detailAddress,
    int sortOrder
) {
    public static ProfileAddressResponse from(ProfileAddress address) {
        return new ProfileAddressResponse(
            address.getProfileId(),
            address.getDeliveryName(),
            address.getZipcode(),
            address.getSido(),
            address.getSigungu(),
            address.getRoadName(),
            address.getBuildingNumber(),
            address.getBuildingName(),
            address.getDetailAddress(),
            address.getSortOrder()
        );
    }
}
