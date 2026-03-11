package com.example.clients.product.config;

import com.example.clients.product.facade.ProductClientFacade;
import com.example.clients.product.facade.ProductEndingSoonClientFacade;
import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.clients.product.facade.ProductQuoteClientFacade;
import com.example.clients.product.facade.ProductItemSummaryClientFacade;
import com.example.clients.product.impl.DefaultProductClientFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class ProductClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DefaultProductClientFacade defaultProductClientFacade(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.clients.product.base-url:${app.service.product-url:http://localhost:8084}}") String productServiceUrl
    ) {
        return new DefaultProductClientFacade(webClientBuilder, objectMapper, productServiceUrl);
    }

    @Bean
    @ConditionalOnMissingBean(ProductClientFacade.class)
    public ProductClientFacade productClientFacade(DefaultProductClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(ProductItemSummaryClientFacade.class)
    public ProductItemSummaryClientFacade productItemSummaryClientFacade(DefaultProductClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(ProductItemQueryClientFacade.class)
    public ProductItemQueryClientFacade productItemQueryClientFacade(DefaultProductClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(ProductQuoteClientFacade.class)
    public ProductQuoteClientFacade productQuoteClientFacade(DefaultProductClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(ProductEndingSoonClientFacade.class)
    public ProductEndingSoonClientFacade productEndingSoonClientFacade(DefaultProductClientFacade delegate) {
        return delegate;
    }
}
