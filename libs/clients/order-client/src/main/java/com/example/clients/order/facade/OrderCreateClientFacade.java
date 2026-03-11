package com.example.clients.order.facade;

import com.example.clients.order.dto.OrderCreateRequest;
import com.example.clients.order.dto.OrderCreateResponse;

public interface OrderCreateClientFacade {

    OrderCreateResponse createOrder(OrderCreateRequest request);
}
