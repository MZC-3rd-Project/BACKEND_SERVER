package com.example.profile.dto.response;

import com.example.profile.entity.ProfileAddress;
import org.springframework.util.StringUtils;

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

    public static String buildFullAddress(ProfileAddress address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.getSido()).append(" ")
          .append(address.getSigungu()).append(" ")
          .append(address.getRoadName()).append(" ")
          .append(address.getBuildingNumber());
        if (StringUtils.hasText(address.getBuildingName())) {
            sb.append(" ").append(address.getBuildingName());
        }
        if (StringUtils.hasText(address.getDetailAddress())) {
            sb.append(" ").append(address.getDetailAddress());
        }
        return sb.toString();
    }
}
