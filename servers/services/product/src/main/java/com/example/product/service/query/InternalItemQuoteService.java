package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.data.entity.datasource.UseWriteDataSource;
import com.example.product.dto.item.request.ItemQuoteRequest;
import com.example.product.dto.item.response.ItemQuoteResponse;
import com.example.product.entity.item.Item;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemRepository;
import com.example.product.service.query.quote.QuoteLineItemCommand;
import com.example.product.service.query.quote.QuoteStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@UseWriteDataSource
public class InternalItemQuoteService {

    private final ItemRepository itemRepository;
    private final ItemAccessPolicy itemAccessPolicy;
    private final List<QuoteStrategy> quoteStrategies;

    public ItemQuoteResponse quote(ItemQuoteRequest request) {
        List<ItemQuoteResponse.QuotedLineItem> quotedLineItems = new ArrayList<>();
        long totalAmount = 0L;

        for (ItemQuoteRequest.LineItem rawLineItem : request.getLineItems()) {
            QuoteLineItemCommand lineItem = QuoteLineItemCommand.from(rawLineItem);
            Item item = itemRepository.findById(lineItem.itemId())
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

            itemAccessPolicy.validateSaleable(item, lineItem.channelType().saleableStatus());
            ItemQuoteResponse.QuotedLineItem quotedLineItem = resolveStrategy(item).quote(item, lineItem);

            quotedLineItems.add(quotedLineItem);
            totalAmount += quotedLineItem.getLineAmount();
        }

        return ItemQuoteResponse.builder()
                .quotedAt(LocalDateTime.now())
                .totalAmount(totalAmount)
                .lineItems(quotedLineItems)
                .build();
    }

    private QuoteStrategy resolveStrategy(Item item) {
        return quoteStrategies.stream()
                .filter(strategy -> strategy.supports(item.getItemType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_TYPE_MISMATCH));
    }
}
