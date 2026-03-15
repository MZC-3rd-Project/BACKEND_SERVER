package com.example.product.service.query.quote;

import com.example.core.exception.BusinessException;
import com.example.product.dto.item.response.ItemQuoteResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PerformanceQuoteStrategy implements QuoteStrategy {

    private final SeatGradeRepository seatGradeRepository;
    private final PerformanceRepository performanceRepository;

    @Override
    public boolean supports(ItemType itemType) {
        return itemType == ItemType.PERFORMANCE;
    }

    @Override
    public ItemQuoteResponse.QuotedLineItem quote(Item item, QuoteLineItemCommand lineItem) {
        SeatGrade seatGrade = seatGradeRepository.findById(lineItem.referenceId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.SEAT_GRADE_NOT_FOUND));
        Performance performance = performanceRepository.findById(seatGrade.getPerformanceId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PERFORMANCE_NOT_FOUND));

        if (!item.getId().equals(performance.getItemId())) {
            throw new BusinessException(ProductErrorCode.SEAT_GRADE_NOT_FOUND);
        }

        long finalUnitPrice = seatGrade.getPrice();
        long lineAmount = finalUnitPrice * lineItem.quantity();

        return ItemQuoteResponse.QuotedLineItem.builder()
                .itemId(item.getId())
                .itemType(item.getItemType().name())
                .title(item.getTitle())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .referenceId(seatGrade.getId())
                .referenceName(seatGrade.getGradeName())
                .stockItemType("SEAT_GRADE")
                .quantity(lineItem.quantity())
                .baseUnitPrice(item.getPrice())
                .finalUnitPrice(finalUnitPrice)
                .lineAmount(lineAmount)
                .build();
    }
}
