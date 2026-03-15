package com.example.product.service.query.quote;

import com.example.product.dto.item.response.ItemQuoteResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;

public interface QuoteStrategy {

    boolean supports(ItemType itemType);

    ItemQuoteResponse.QuotedLineItem quote(Item item, QuoteLineItemCommand lineItem);
}
