package com.example.clients.stock.config;

import com.example.clients.stock.facade.StockAvailabilityQueryFacade;
import com.example.clients.stock.facade.StockClientFacade;
import com.example.clients.stock.facade.StockInfoQueryClientFacade;
import com.example.clients.stock.facade.StockItemReferenceQueryFacade;
import com.example.clients.stock.facade.StockOrderReservationClientFacade;
import com.example.clients.stock.facade.StockReservationClientFacade;
import com.example.clients.stock.impl.DefaultStockClientFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class StockClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DefaultStockClientFacade defaultStockClientFacade(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.clients.stock.base-url:${app.service.stock-url:http://localhost:8085}}") String stockServiceUrl
    ) {
        return new DefaultStockClientFacade(webClientBuilder, objectMapper, stockServiceUrl);
    }

    @Bean
    @ConditionalOnMissingBean(StockClientFacade.class)
    public StockClientFacade stockClientFacade(DefaultStockClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(StockReservationClientFacade.class)
    public StockReservationClientFacade stockReservationClientFacade(DefaultStockClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(StockOrderReservationClientFacade.class)
    public StockOrderReservationClientFacade stockOrderReservationClientFacade(DefaultStockClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(StockItemReferenceQueryFacade.class)
    public StockItemReferenceQueryFacade stockItemReferenceQueryFacade(DefaultStockClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(StockInfoQueryClientFacade.class)
    public StockInfoQueryClientFacade stockInfoQueryClientFacade(DefaultStockClientFacade delegate) {
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean(StockAvailabilityQueryFacade.class)
    public StockAvailabilityQueryFacade stockAvailabilityQueryFacade(DefaultStockClientFacade delegate) {
        return delegate;
    }
}
