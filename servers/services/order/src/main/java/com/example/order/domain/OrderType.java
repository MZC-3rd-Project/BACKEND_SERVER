package com.example.order.domain;

/**
 * 주문이 어떤 채널을 통해 생성되었는지 구분하는 타입.
 * 하나의 주문에는 하나의 OrderType만 부여된다.
 */
public enum OrderType {

    FUNDING,
    SALE,
    HOTDEAL
}
