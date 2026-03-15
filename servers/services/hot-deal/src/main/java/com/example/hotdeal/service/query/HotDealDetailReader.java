package com.example.hotdeal.service.query;

import com.example.core.exception.BusinessException;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HotDealDetailReader {

    private final HotDealRepository hotDealRepository;

    @UseWriteDataSource
    public HotDealDetailView read(Long hotDealId) {
        HotDeal hotDeal = hotDealRepository.findById(hotDealId)
                .orElseThrow(() -> new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND));
        return new HotDealDetailView(
                hotDeal.getId(),
                hotDeal.getItemId(),
                hotDeal.getTitle(),
                hotDeal.getOriginalPrice(),
                hotDeal.getDiscountRate(),
                hotDeal.getDiscountedPrice(),
                hotDeal.getMaxQuantity(),
                hotDeal.getMaxPerUser(),
                hotDeal.getSoldQuantity(),
                hotDeal.getRemainingQuantity(),
                hotDeal.getStatus(),
                hotDeal.getStartAt(),
                hotDeal.getEndAt(),
                hotDeal.getCreatedAt()
        );
    }
}
