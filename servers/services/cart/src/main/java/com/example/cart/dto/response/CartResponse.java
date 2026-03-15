package com.example.cart.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartResponse {

    private int itemCount;
    private int selectedItemCount;
    private List<CartItemResponse> items;

    public static CartResponse of(List<CartItemResponse> items) {
        int selectedItemCount = (int) items.stream()
                .filter(CartItemResponse::isSelected)
                .count();
        return CartResponse.builder()
                .itemCount(items.size())
                .selectedItemCount(selectedItemCount)
                .items(items)
                .build();
    }
}
