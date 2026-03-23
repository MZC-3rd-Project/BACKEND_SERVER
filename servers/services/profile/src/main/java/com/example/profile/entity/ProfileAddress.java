package com.example.profile.entity;

import com.example.clients.auth.dto.profile.ProfileDeliveryAddressQuery;
import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.profile.dto.request.ProfileAddressRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;


// 배송지 주소
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "profile_delivery_addresses")
public class ProfileAddress extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "profile_id", nullable = false) // userid
    private Long profileId;

    @Column(name = "delivery_name", nullable = false, length = 20)
    private String deliveryName;

    @Column(name = "zipcode", nullable = false, length = 10)
    private String zipcode;

    @Column(name = "sido", nullable = false, length = 30)
    private String sido;

    @Column(name = "sigungu", nullable = false, length = 30)
    private String sigungu;

    @Column(name = "road_name", nullable = false, length = 100)
    private String roadName;

    @Column(name = "building_number", nullable = false, length = 20)
    private String buildingNumber;

    @Column(name = "building_name", length = 100)
    private String buildingName;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    @Column(name = "sort_order", columnDefinition = "INT DEFAULT 0")
    private int sortOrder = 0;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    public void setAsDefault() {
        this.isDefault = true;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }

    // 배송지 수정
    public void update(
        ProfileAddressRequest req
    ) {
        this.deliveryName   = req.deliveryName();
        this.zipcode        = req.zipcode();
        this.sido           = req.sido();
        this.sigungu        = req.sigungu();
        this.roadName       = req.roadName();
        this.buildingNumber = req.buildingNumber();
        this.buildingName   = req.buildingName();
        this.detailAddress  = req.detailAddress();
        this.sortOrder      = req.sortOrder();
    }

    public static ProfileAddress create(Long profileId, ProfileDeliveryAddressQuery req){
        return ProfileAddress.builder()
            .profileId(profileId)
            .deliveryName(req.deliveryName())
            .zipcode(req.zipcode())
            .sido(req.sido())
            .sigungu(req.sigungu())
            .roadName(req.roadName())
            .buildingNumber(req.buildingNumber())
            .buildingName(req.buildingName())
            .detailAddress(req.detailAddress())
            .sortOrder(req.sortOrder())
            .build();
    }


}
