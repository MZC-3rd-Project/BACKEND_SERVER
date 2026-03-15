package com.example.product.service.query.quote;

import com.example.product.dto.item.request.ItemQuoteRequest;

public record QuoteLineItemCommand(
        Long itemId,
        QuoteChannelType channelType,
        Long channelRefId,
        Long referenceId,
        Integer quantity
) {
    public static QuoteLineItemCommand from(ItemQuoteRequest.LineItem lineItem) {
        return new QuoteLineItemCommand(
                lineItem.getItemId(),
                QuoteChannelType.from(lineItem.getChannelType()),
                lineItem.getChannelRefId(),
                lineItem.getReferenceId(),
                lineItem.getQuantity()
        );
    }
}
