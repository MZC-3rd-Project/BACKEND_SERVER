package com.example.hotdeal.service.query;

import com.example.hotdeal.dto.query.response.HotDealDetailQueryResponse;
import com.example.hotdeal.dto.query.response.HotDealListQueryResponse;
import com.example.hotdeal.entity.HotDeal;
import org.springframework.stereotype.Component;

@Component
public class HotDealQueryAssembler {

    public HotDealListQueryResponse toListResponse(HotDeal hotDeal) {
        return HotDealListQueryResponse.builder()
                .id(hotDeal.getId())
                .title(hotDeal.getTitle())
                .discountRate(hotDeal.getDiscountRate())
                .discountedPrice(hotDeal.getDiscountedPrice())
                .soldQuantity(hotDeal.getSoldQuantity())
                .maxQuantity(hotDeal.getMaxQuantity())
                .endAt(hotDeal.getEndAt())
                .build();
    }

    public HotDealDetailQueryResponse toDetailResponse(HotDealDetailView view) {
        double progress = view.maxQuantity() > 0
                ? (double) view.soldQuantity() / view.maxQuantity() * 100
                : 0.0;

        return HotDealDetailQueryResponse.builder()
                .id(view.id())
                .itemId(view.itemId())
                .title(view.title())
                .originalPrice(view.originalPrice())
                .discountRate(view.discountRate())
                .discountedPrice(view.discountedPrice())
                .maxQuantity(view.maxQuantity())
                .maxPerUser(view.maxPerUser())
                .soldQuantity(view.soldQuantity())
                .remainingQuantity(view.remainingQuantity())
                .progressRate(Math.round(progress * 10.0) / 10.0)
                .status(view.status().name())
                .startAt(view.startAt())
                .endAt(view.endAt())
                .createdAt(view.createdAt())
                .build();
    }
}
