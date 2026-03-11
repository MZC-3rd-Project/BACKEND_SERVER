package com.example.clients.order.config;

import com.example.clients.order.facade.OrderCreateClientFacade;
import com.example.clients.order.impl.DefaultOrderClientFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class OrderClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DefaultOrderClientFacade defaultOrderClientFacade(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.clients.order.base-url:${app.service.order-url:http://localhost:8090}}") String orderServiceUrl
    ) {
        return new DefaultOrderClientFacade(webClientBuilder, objectMapper, orderServiceUrl);
    }

    @Bean
    @ConditionalOnMissingBean(OrderCreateClientFacade.class)
    public OrderCreateClientFacade orderCreateClientFacade(DefaultOrderClientFacade delegate) {
        return delegate;
    }
}
