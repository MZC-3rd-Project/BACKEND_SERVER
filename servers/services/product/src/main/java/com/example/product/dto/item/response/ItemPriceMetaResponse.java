package com.example.product.dto.item.response;

import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.performance.SeatGrade;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;

@Getter
@Builder
public class ItemPriceMetaResponse {

    private Long basePrice;
    private Long startingPrice;
    private Long minPrice;
    private Long maxPrice;

    public static ItemPriceMetaResponse fromOptions(Long basePrice, List<ItemOption> options) {
        if (options == null || options.isEmpty()) {
            return fixed(basePrice);
        }

        List<Long> finalPrices = options.stream()
                .map(option -> basePrice + option.getAdditionalPrice())
                .toList();
        return ItemPriceMetaResponse.builder()
                .basePrice(basePrice)
                .startingPrice(finalPrices.stream().min(Comparator.naturalOrder()).orElse(basePrice))
                .minPrice(finalPrices.stream().min(Comparator.naturalOrder()).orElse(basePrice))
                .maxPrice(finalPrices.stream().max(Comparator.naturalOrder()).orElse(basePrice))
                .build();
    }

    public static ItemPriceMetaResponse fromSeatGrades(Long basePrice, List<SeatGrade> seatGrades) {
        if (seatGrades == null || seatGrades.isEmpty()) {
            return fixed(basePrice);
        }

        List<Long> prices = seatGrades.stream()
                .map(SeatGrade::getPrice)
                .toList();
        return ItemPriceMetaResponse.builder()
                .basePrice(basePrice)
                .startingPrice(prices.stream().min(Comparator.naturalOrder()).orElse(basePrice))
                .minPrice(prices.stream().min(Comparator.naturalOrder()).orElse(basePrice))
                .maxPrice(prices.stream().max(Comparator.naturalOrder()).orElse(basePrice))
                .build();
    }

    public static ItemPriceMetaResponse fixed(Long basePrice) {
        return ItemPriceMetaResponse.builder()
                .basePrice(basePrice)
                .startingPrice(basePrice)
                .minPrice(basePrice)
                .maxPrice(basePrice)
                .build();
    }
}
