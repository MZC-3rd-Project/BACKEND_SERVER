package com.example.sales.service.command;

import com.example.clients.order.exception.OrderClientException;

public interface CheckoutSubmitFailurePolicy {

    CheckoutSubmitFailureDecision decide(OrderClientException exception);
}
