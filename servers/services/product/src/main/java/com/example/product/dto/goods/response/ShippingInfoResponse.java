package com.example.product.dto.goods.response;

import com.example.product.entity.goods.ShippingInfo;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShippingInfoResponse {

    private Long shippingFee;
    private Long freeShippingThreshold;
    private Integer estimatedDays;
    private String returnPolicy;
    private String carrier;
    private String shipFrom;
    private String returnAddress;
    private Long returnShippingFee;
    private Long exchangeShippingFee;
    private String shippingNotice;

    public static ShippingInfoResponse from(ShippingInfo entity) {
        return ShippingInfoResponse.builder()
                .shippingFee(entity.getShippingFee())
                .freeShippingThreshold(entity.getFreeShippingThreshold())
                .estimatedDays(entity.getEstimatedDays())
                .returnPolicy(entity.getReturnPolicy())
                .carrier(entity.getCarrier())
                .shipFrom(entity.getShipFrom())
                .returnAddress(entity.getReturnAddress())
                .returnShippingFee(entity.getReturnShippingFee())
                .exchangeShippingFee(entity.getExchangeShippingFee())
                .shippingNotice(entity.getShippingNotice())
                .build();
    }
}
