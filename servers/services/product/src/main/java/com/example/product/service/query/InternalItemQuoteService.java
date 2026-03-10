package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.product.dto.item.request.ItemQuoteRequest;
import com.example.product.dto.item.response.ItemQuoteResponse;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@UseWriteDataSource
public class InternalItemQuoteService {

    private final ItemRepository itemRepository;
    private final ItemOptionRepository itemOptionRepository;
    private final PerformanceRepository performanceRepository;
    private final SeatGradeRepository seatGradeRepository;

    public ItemQuoteResponse quote(ItemQuoteRequest request) {
        validateChannelType(request.getChannelType());

        List<ItemQuoteResponse.QuotedLineItem> quotedLineItems = new ArrayList<>();
        long totalAmount = 0L;

        for (ItemQuoteRequest.LineItem lineItem : request.getLineItems()) {
            Item item = itemRepository.findById(lineItem.getItemId())
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

            validateSaleable(item, request.getChannelType());

            ItemQuoteResponse.QuotedLineItem quotedLineItem = switch (item.getItemType()) {
                case PRODUCT, GOODS -> quoteOptionItem(item, lineItem);
                case PERFORMANCE -> quotePerformanceItem(item, lineItem);
            };

            quotedLineItems.add(quotedLineItem);
            totalAmount += quotedLineItem.getLineAmount();
        }

        return ItemQuoteResponse.builder()
                .quotedAt(LocalDateTime.now())
                .totalAmount(totalAmount)
                .lineItems(quotedLineItems)
                .build();
    }

    private ItemQuoteResponse.QuotedLineItem quoteOptionItem(Item item, ItemQuoteRequest.LineItem lineItem) {
        ItemOption option = itemOptionRepository.findById(lineItem.getReferenceId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

        if (!item.getId().equals(option.getItemId())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        long baseUnitPrice = item.getPrice();
        long finalUnitPrice = baseUnitPrice + option.getAdditionalPrice();
        long lineAmount = finalUnitPrice * lineItem.getQuantity();

        return ItemQuoteResponse.QuotedLineItem.builder()
                .itemId(item.getId())
                .itemType(item.getItemType().name())
                .title(item.getTitle())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .referenceId(option.getId())
                .referenceName(option.getOptionName())
                .stockItemType("ITEM_OPTION")
                .quantity(lineItem.getQuantity())
                .baseUnitPrice(baseUnitPrice)
                .finalUnitPrice(finalUnitPrice)
                .lineAmount(lineAmount)
                .build();
    }

    private ItemQuoteResponse.QuotedLineItem quotePerformanceItem(Item item, ItemQuoteRequest.LineItem lineItem) {
        SeatGrade seatGrade = seatGradeRepository.findById(lineItem.getReferenceId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.SEAT_GRADE_NOT_FOUND));
        Performance performance = performanceRepository.findById(seatGrade.getPerformanceId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PERFORMANCE_NOT_FOUND));

        if (!item.getId().equals(performance.getItemId())) {
            throw new BusinessException(ProductErrorCode.SEAT_GRADE_NOT_FOUND);
        }

        long finalUnitPrice = seatGrade.getPrice();
        long lineAmount = finalUnitPrice * lineItem.getQuantity();

        return ItemQuoteResponse.QuotedLineItem.builder()
                .itemId(item.getId())
                .itemType(item.getItemType().name())
                .title(item.getTitle())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .referenceId(seatGrade.getId())
                .referenceName(seatGrade.getGradeName())
                .stockItemType("SEAT_GRADE")
                .quantity(lineItem.getQuantity())
                .baseUnitPrice(item.getPrice())
                .finalUnitPrice(finalUnitPrice)
                .lineAmount(lineAmount)
                .build();
    }

    private void validateChannelType(String channelType) {
        if (channelType == null) {
            throw new BusinessException(ProductErrorCode.INVALID_QUOTE_CHANNEL);
        }

        String normalized = channelType.trim().toUpperCase(Locale.ROOT);
        if (!"NORMAL".equals(normalized) && !"FUNDING".equals(normalized)) {
            throw new BusinessException(ProductErrorCode.INVALID_QUOTE_CHANNEL);
        }
    }

    private void validateSaleable(Item item, String channelType) {
        String normalized = channelType.trim().toUpperCase(Locale.ROOT);
        ItemStatus expectedStatus = "FUNDING".equals(normalized) ? ItemStatus.FUNDING : ItemStatus.ON_SALE;
        if (item.getStatus() != expectedStatus) {
            throw new BusinessException(ProductErrorCode.ITEM_NOT_SALEABLE);
        }
    }
}
