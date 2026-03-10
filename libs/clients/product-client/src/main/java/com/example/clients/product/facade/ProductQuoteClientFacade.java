package com.example.clients.product.facade;

import com.example.clients.product.dto.ProductQuoteRequest;
import com.example.clients.product.dto.ProductQuoteResponse;

public interface ProductQuoteClientFacade {

    ProductQuoteResponse quoteItems(ProductQuoteRequest request);
}
