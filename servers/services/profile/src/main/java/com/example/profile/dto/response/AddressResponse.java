package com.example.profile.dto.response;

import com.example.profile.entity.ProfileAddress;
import org.springframework.util.StringUtils;

public record AddressResponse(
    Long id,
    String deliveryName,
    boolean isDefault,
    String fullAddress
) {
    public static AddressResponse from(ProfileAddress address) {
        return new AddressResponse(
            address.getId(),
            address.getDeliveryName(),
            address.isDefault(),
            buildFullAddress(address)
        );
    }

    private static String buildFullAddress(ProfileAddress address) {
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
