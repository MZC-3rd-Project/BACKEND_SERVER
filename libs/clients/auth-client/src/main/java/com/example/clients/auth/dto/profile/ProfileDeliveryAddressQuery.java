package com.example.clients.auth.dto.profile;

public record ProfileDeliveryAddressQuery(
    String deliveryName,
    String zipcode,
    String sido,
    String sigungu,
    String roadName,
    String buildingNumber,
    String buildingName,
    String detailAddress,
    Integer sortOrder
) {
}
