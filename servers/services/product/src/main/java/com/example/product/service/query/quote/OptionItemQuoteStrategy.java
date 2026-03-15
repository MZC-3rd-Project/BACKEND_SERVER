package com.example.product.service.query.quote;

import com.example.core.exception.BusinessException;
import com.example.product.dto.item.response.ItemQuoteResponse;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OptionItemQuoteStrategy implements QuoteStrategy {

    private final ItemOptionRepository itemOptionRepository;

    @Override
    public boolean supports(ItemType itemType) {
        return itemType == ItemType.PRODUCT || itemType == ItemType.GOODS;
    }

    @Override
    public ItemQuoteResponse.QuotedLineItem quote(Item item, QuoteLineItemCommand lineItem) {
        ItemOption option = itemOptionRepository.findById(lineItem.referenceId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

        if (!item.getId().equals(option.getItemId())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        long baseUnitPrice = item.getPrice();
        long finalUnitPrice = baseUnitPrice + option.getAdditionalPrice();
        long lineAmount = finalUnitPrice * lineItem.quantity();

        return ItemQuoteResponse.QuotedLineItem.builder()
                .itemId(item.getId())
                .itemType(item.getItemType().name())
                .title(item.getTitle())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .referenceId(option.getId())
                .referenceName(option.getOptionName())
                .stockItemType("ITEM_OPTION")
                .quantity(lineItem.quantity())
                .baseUnitPrice(baseUnitPrice)
                .finalUnitPrice(finalUnitPrice)
                .lineAmount(lineAmount)
                .build();
    }
}
