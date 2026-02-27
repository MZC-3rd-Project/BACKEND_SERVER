package com.example.clients.product.facade;

import com.example.clients.product.dto.ProductItemSummary;

public interface ProductItemSummaryClientFacade {

    ProductItemSummary findItemSummary(Long itemId);
}
