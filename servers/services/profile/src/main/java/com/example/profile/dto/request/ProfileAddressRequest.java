package com.example.profile.dto.request;

import java.io.Serializable;


public record ProfileAddressRequest(
    String deliveryName,
    String zipcode,
    String sido,
    String sigungu,
    String roadName,
    String buildingNumber,
    String buildingName,
    String detailAddress,
    int sortOrder) {
}
